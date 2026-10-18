package com.lunlun.threegoodthings;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real native view actions, persistence and document IO. Use a test emulator only. */
public class NativeSmokeTest extends Instrumentation {
    private MainActivity activity;
    private List<JournalStore.Entry> previous;
    private File exported, imported, fixture;
    private String backupFixture;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            stage("Testing native data model");testModel();stage("Data model passed; launching native activity");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            stage("Native activity launched");
            check(activity.store.readable,"Initial storage unreadable");previous=activity.store.entries();
            runOnMainSync(()->activity.findViewById(android.R.id.content).findViewWithTag("add-entry").performClick());
            runOnMainSync(()->{
                ((EditText)activity.editor.getWindow().getDecorView().findViewWithTag("entry-text")).setText("晚上吃到了我最喜歡的巧克力");
                Spinner category=activity.editor.getWindow().getDecorView().findViewWithTag("entry-category");category.setSelection(category.getCount()-1);
            });waitForIdleSync();
            runOnMainSync(()->{
                ((EditText)activity.editor.getWindow().getDecorView().findViewWithTag("custom-category")).setText("美食時光");
                ((EditText)activity.editor.getWindow().getDecorView().findViewWithTag("entry-reflection")).setText("和家人一起吃晚餐。");
                activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            check(activity.store.entries().size()==previous.size()+1,"Native create failed");
            JournalStore.Entry added=activity.store.entries().get(previous.size());
            check(added.category.equals("美食時光"),"Custom category not saved");
            check(new JournalStore(new File(getTargetContext().getFilesDir(),"entries.json")).entries().size()==previous.size()+1,"Disk persistence failed");
            runOnMainSync(()->activity.finish());
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            stage("Native activity restarted");
            check(activity.store.entries().size()==previous.size()+1,"Activity restart lost data");
            click("edit-"+added.id);
            runOnMainSync(()->{((EditText)activity.editor.getWindow().getDecorView().findViewWithTag("entry-text")).setText("巧克力很好吃");activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
            check(activity.store.entries().get(previous.size()).text.equals("巧克力很好吃"),"Native edit failed");
            runOnMainSync(()->((Spinner)activity.findViewById(android.R.id.content).findViewWithTag("category-filter")).setSelection(3));waitForIdleSync();
            check(ui(()->activity.findViewById(android.R.id.content).findViewWithTag("entry-text-"+added.id)==null),"Category filter failed");
            runOnMainSync(()->((Spinner)activity.findViewById(android.R.id.content).findViewWithTag("category-filter")).setSelection(0));waitForIdleSync();
            click("nav-calendar");click("calendar-"+added.date);
            check(ui(()->activity.findViewById(android.R.id.content).findViewWithTag("entry-text-"+added.id)!=null),"Calendar review failed");
            stage("Native create/edit, persistence, filter and calendar passed; testing documents");
            click("nav-backup");
            exported=new File(getTargetContext().getCacheDir(),"native-smoke-export.json");exported.delete();
            ActivityMonitor save=addMonitor(documentFilter(Intent.ACTION_CREATE_DOCUMENT),new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(exported))),true);
            click("export-backup");waitUntil(()->exported.exists()&&exported.length()>0);
            List<JournalStore.Entry> restored=JournalStore.readBackup(new String(Files.readAllBytes(exported.toPath()),StandardCharsets.UTF_8));
            check(restored.stream().anyMatch(entry->entry.id.equals(added.id)&&entry.text.equals("巧克力很好吃")),"Native exported contents incorrect");
            check(checkMonitorHit(save,1),"Export did not use document creator");
            String importedId=UUID.randomUUID().toString();
            JournalStore.Entry incoming=new JournalStore.Entry(importedId,added.date,"從網頁版匯入","","美食時光");
            JSONObject backup=new JSONObject().put("app","three-good-things").put("version",1).put("entries",JournalStore.array(Arrays.asList(incoming)));
            imported=new File(getTargetContext().getCacheDir(),"native-smoke-import.json");Files.write(imported.toPath(),backup.toString().getBytes(StandardCharsets.UTF_8));
            for(int repeat=0;repeat<2;repeat++) {
                runOnMainSync(()->activity.confirmation=null);
                ActivityMonitor open=addMonitor(documentFilter(Intent.ACTION_OPEN_DOCUMENT),new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(imported))),true);
                click("import-backup");waitUntil(()->ui(()->activity.confirmation!=null&&activity.confirmation.isShowing()));
                runOnMainSync(()->activity.confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();
                check(checkMonitorHit(open,1),"Import did not use document picker");
                check(activity.store.entries().size()==previous.size()+2,"Import failed or duplicated entries");
            }
            click("nav-daily");click("delete-"+added.id);
            runOnMainSync(()->activity.confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());waitForIdleSync();
            check(activity.store.entries().stream().anyMatch(entry->entry.id.equals(added.id)),"Canceled delete changed data");
            click("delete-"+added.id);runOnMainSync(()->activity.confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();
            check(activity.store.entries().stream().noneMatch(entry->entry.id.equals(added.id)),"Native delete failed");
            cleanup();
            result.putString("stream","PASS: native create/edit/delete, custom category, filtering, calendar review, activity restart and atomic disk persistence, JSON document export/import and repeated import, malformed backup rejection and corrupt-storage protection.\n");
            result.putString("backup_fixture",backupFixture);
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            try{cleanup();}catch(Throwable ignored){}
            result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void testModel() throws Exception {
        fixture=new File(getTargetContext().getCacheDir(),"native-smoke-store.json");fixture.delete();
        JournalStore store=new JournalStore(fixture);
        JournalStore.Entry entry=new JournalStore.Entry("one",LocalDate.of(2026,10,18),"生活小事","","自訂分類");
        store.save(Arrays.asList(entry));check(new JournalStore(fixture).entries().get(0).category.equals("自訂分類"),"Store reload failed");
        backupFixture=store.backup();
        check(JournalStore.readBackup(backupFixture).get(0).id.equals("one"),"Backup round trip failed");
        try(InputStream input=getContext().getAssets().open("web-backup.json")) {
            JournalStore.Entry web=JournalStore.readBackup(JournalStore.readText(input)).get(0);
            check(web.id.equals("web-fixture")&&web.category.equals("美食時光")&&web.reflection.equals("和家人一起吃晚餐。"),"Web-exported backup incompatible");
        }
        check(JournalStore.merge(store.entries(),store.entries()).size()==1,"Merge not idempotent");
        boolean rejected=false;try{JournalStore.readBackup("{bad");}catch(Exception error){rejected=true;}check(rejected,"Malformed JSON accepted");
        rejected=false;try{JournalStore.validate(JournalStore.array(Arrays.asList(entry,entry)));}catch(Exception error){rejected=true;}check(rejected,"Duplicate IDs accepted");
        JSONObject bad=entry.json().put("date","2026-02-30");rejected=false;try{JournalStore.validate(new JSONArray().put(bad));}catch(Exception error){rejected=true;}check(rejected,"Invalid date accepted");
        Files.write(fixture.toPath(),"corrupt".getBytes(StandardCharsets.UTF_8));JournalStore corrupt=new JournalStore(fixture);check(!corrupt.readable,"Corrupt storage not detected");
        rejected=false;try{corrupt.save(Arrays.asList(entry));}catch(Exception error){rejected=true;}check(rejected,"Corrupt storage overwritten");check(new String(Files.readAllBytes(fixture.toPath()),StandardCharsets.UTF_8).equals("corrupt"),"Corrupt original not preserved");fixture.delete();
    }
    private void stage(String message){Bundle status=new Bundle();status.putString("stream",message+"\n");sendStatus(0,status);}
    private IntentFilter documentFilter(String action) throws IntentFilter.MalformedMimeTypeException {
        IntentFilter filter=new IntentFilter(action);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("*/*");return filter;
    }
    private void click(String tag) {runOnMainSync(()->{View view=activity.findViewById(android.R.id.content).findViewWithTag(tag);if(view==null)throw new AssertionError("Missing view "+tag);view.performClick();});waitForIdleSync();}
    private boolean ui(BooleanSupplier condition){AtomicBoolean value=new AtomicBoolean();runOnMainSync(()->value.set(condition.getAsBoolean()));return value.get();}
    private void waitUntil(BooleanSupplier condition)throws Exception{long deadline=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<deadline){if(condition.getAsBoolean())return;Thread.sleep(100);}throw new AssertionError("Native operation timed out");}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void cleanup() throws Exception {
        if(activity!=null){if(previous!=null)runOnMainSync(()->{try{activity.store.save(previous);}catch(Exception error){throw new RuntimeException(error);}});runOnMainSync(()->activity.finish());}
        if(exported!=null)exported.delete();if(imported!=null)imported.delete();if(fixture!=null)fixture.delete();
    }
}
