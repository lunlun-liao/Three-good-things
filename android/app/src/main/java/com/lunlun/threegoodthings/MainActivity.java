package com.lunlun.threegoodthings;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Fully native Android UI. No WebView, browser, server or network permission. */
public class MainActivity extends Activity {
    static final int BG = Color.rgb(255,250,240), INK = Color.rgb(76,56,36), MUTED = Color.rgb(123,104,82), YELLOW = Color.rgb(243,198,78), PALE = Color.rgb(255,240,189);
    private static final int IMPORT = 100, EXPORT = 101;
    JournalStore store;
    AlertDialog editor, confirmation;
    private LocalDate selected = LocalDate.now();
    private YearMonth month = YearMonth.from(selected);
    private String page = "daily", filter = "", pendingBackup;
    private LinearLayout root, content, list;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy年 M月d日 EEEE", Locale.TRADITIONAL_CHINESE);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new JournalStore(new File(getFilesDir(), "entries.json"));
        if (state != null) {
            try { selected = LocalDate.parse(state.getString("selected")); month = YearMonth.parse(state.getString("month")); } catch (Exception ignored) { }
            page = state.getString("page", "daily"); filter = state.getString("filter", "");
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("selected", selected.toString()); state.putString("month", month.toString()); state.putString("page", page); state.putString("filter", filter);
        super.onSaveInstanceState(state);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = new LinearLayout(this); box.setGravity(Gravity.CENTER_VERTICAL); return box; }
    private GradientDrawable background(int color, boolean border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(14));
        if (border) shape.setStroke(dp(1), Color.rgb(238,225,199));
        return shape;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(INK); label.setLineSpacing(dp(4), 1);
        if (bold) label.setTypeface(null, Typeface.BOLD);
        return label;
    }
    private void space(LinearLayout box, int height) { View gap = new View(this); box.addView(gap, new LinearLayout.LayoutParams(1, dp(height))); }
    private Button button(String title, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(INK); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(44));
        button.setPadding(dp(12), dp(5), dp(12), dp(5)); button.setBackground(background(primary ? YELLOW : PALE, false)); button.setOnClickListener(v -> action.run());
        return button;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column(); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackground(background(Color.WHITE, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14); parent.addView(card, params); return card;
    }
    private void message(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    void render() {
        list = null; root = column(); root.setBackgroundColor(BG); setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
            WindowInsetsController controls=getWindow().getInsetsController();
            if(controls!=null) controls.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else root.setFitsSystemWindows(true);
        TextView brand = text("三件好事", 24, true); brand.setPadding(dp(20), dp(20), dp(20), dp(12)); root.addView(brand);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column(); content.setPadding(dp(20),dp(8),dp(20),dp(24)); scroll.addView(content);
        if (!store.readable) { TextView warning = text("無法讀取原有資料。為保護紀錄，暫停新增與匯入；請勿清除 APP 資料。", 15, false); warning.setPadding(dp(12),dp(12),dp(12),dp(12)); warning.setBackground(background(PALE,false)); content.addView(warning); space(content,16); }
        if (page.equals("backup")) renderBackup(); else if (page.equals("calendar")) renderCalendar(); else renderDaily();
        LinearLayout nav = row(); nav.setPadding(dp(12),dp(8),dp(12),dp(8)); root.addView(nav);
        String[] titles = {"每日好事","日曆回顧","備份與資料"}, pages = {"daily","calendar","backup"};
        for (int i=0;i<titles.length;i++) {
            final String next = pages[i]; Button item = button(titles[i], page.equals(next), () -> { page = next; render(); }); item.setTag("nav-"+next);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,dp(52),1); params.setMargins(dp(3),0,dp(3),0); nav.addView(item,params);
        }
    }
    private void changeDate(LocalDate value) { if (!JournalStore.validDate(value)) return; selected=value; month=YearMonth.from(value); render(); }
    private void pickDate(LocalDate date, java.util.function.Consumer<LocalDate> apply) {
        DatePickerDialog dialog = new DatePickerDialog(this, (picker,y,m,d) -> apply.accept(LocalDate.of(y,m+1,d)), date.getYear(), date.getMonthValue()-1,date.getDayOfMonth());
        dialog.getDatePicker().setMinDate(JournalStore.MIN_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dialog.getDatePicker().setMaxDate(JournalStore.MAX_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()); dialog.show();
    }
    private List<JournalStore.Entry> daily() {
        List<JournalStore.Entry> result = new ArrayList<>(); for (JournalStore.Entry entry : store.entries()) if (entry.date.equals(selected)) result.add(entry); return result;
    }
    private void renderDaily() {
        content.addView(text("每日好事", 21, true)); space(content,16);
        LinearLayout dates = row(); content.addView(dates);
        Button previous=button("‹",false,()->changeDate(selected.minusDays(1))); previous.setContentDescription("前一天"); previous.setEnabled(selected.isAfter(JournalStore.MIN_DATE)); dates.addView(previous,new LinearLayout.LayoutParams(dp(44),dp(48)));
        Button choose=button(selected.format(dateFormat),false,()->pickDate(selected,this::changeDate)); choose.setTag("choose-date"); LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(0,dp(52),1); dateParams.setMargins(dp(5),0,dp(5),0); dates.addView(choose,dateParams);
        Button next=button("›",false,()->changeDate(selected.plusDays(1))); next.setContentDescription("後一天"); next.setEnabled(selected.isBefore(JournalStore.MAX_DATE)); dates.addView(next,new LinearLayout.LayoutParams(dp(44),dp(48)));
        space(content,16); LinearLayout progress=card(content); progress.setBackground(background(PALE,false));
        int count=daily().size(); progress.addView(text("已記錄 "+count+" 件好事",18,true));
        StringBuilder dots=new StringBuilder(); for(int i=0;i<3;i++) dots.append(i<count ? "●  " : "○  "); space(progress,8); progress.addView(text(dots.toString(),20,true));
        addList();
    }
    private void renderCalendar() {
        content.addView(text("日曆回顧",21,true)); space(content,16);
        LinearLayout calendar=card(content), header=row(); calendar.addView(header);
        Button previous=button("‹",false,()->{month=month.minusMonths(1); render();}); previous.setContentDescription("上個月"); previous.setEnabled(month.isAfter(YearMonth.from(JournalStore.MIN_DATE))); header.addView(previous,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text(month.getYear()+"年 "+month.getMonthValue()+"月",18,true); title.setGravity(Gravity.CENTER); header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        Button next=button("›",false,()->{month=month.plusMonths(1); render();}); next.setContentDescription("下個月"); next.setEnabled(month.isBefore(YearMonth.from(JournalStore.MAX_DATE))); header.addView(next,new LinearLayout.LayoutParams(dp(44),dp(44)));
        space(calendar,12); LinearLayout weekdays=row(); calendar.addView(weekdays);
        for(String name:new String[]{"日","一","二","三","四","五","六"}) { TextView label=text(name,13,false); label.setGravity(Gravity.CENTER); weekdays.addView(label,new LinearLayout.LayoutParams(0,dp(32),1)); }
        Map<LocalDate,Integer> counts=new HashMap<>(); for(JournalStore.Entry entry:store.entries()) counts.put(entry.date,counts.getOrDefault(entry.date,0)+1);
        int offset=month.atDay(1).getDayOfWeek().getValue()%7, cells=((offset+month.lengthOfMonth()+6)/7)*7;
        LinearLayout week=null;
        for(int cell=0;cell<cells;cell++) {
            if(cell%7==0) {week=row(); calendar.addView(week);}
            int day=cell-offset+1; LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(52),1); params.setMargins(dp(1),dp(2),dp(1),dp(2));
            if(day<1 || day>month.lengthOfMonth()) {week.addView(new View(this),params);continue;}
            LocalDate date=month.atDay(day); int count=counts.getOrDefault(date,0);
            Button dateButton=button(day+(count>0 ? "\n•" : ""),date.equals(selected),()->{selected=date;render();}); dateButton.setTextSize(13); dateButton.setPadding(0,0,0,0); dateButton.setContentDescription(date+"，"+count+" 件好事"); dateButton.setTag("calendar-"+date); week.addView(dateButton,params);
        }
        space(calendar,10); calendar.addView(text("圓點表示當天有紀錄，點日期即可查看。",12,false));
        content.addView(button("回到今天",false,()->changeDate(LocalDate.now()))); space(content,16); addList();
    }
    private List<String> categories() {
        Set<String> names=new LinkedHashSet<>(Arrays.asList(JournalStore.DEFAULT_CATEGORIES)); for(JournalStore.Entry entry:store.entries()) names.add(entry.category); return new ArrayList<>(names);
    }
    private Spinner spinner(List<String> options) {
        Spinner spinner=new Spinner(this); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); spinner.setMinimumHeight(dp(48)); return spinner;
    }
    private void addList() {
        TextView listHeading=text(selected.format(dateFormat)+" 的好事",17,true); content.addView(listHeading); space(content,12);
        LinearLayout toolbar=row(); content.addView(toolbar);
        List<String> options=new ArrayList<>(); options.add("全部分類"); options.addAll(categories());
        Spinner category=spinner(options); category.setTag("category-filter"); int index=filter.isEmpty()?0:options.subList(1,options.size()).indexOf(filter)+1; if(index<0){index=0;filter="";} category.setSelection(index);
        toolbar.addView(category,new LinearLayout.LayoutParams(0,dp(48),1));
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){} public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String next=position==0?"":options.get(position);if(!filter.equals(next)){filter=next;renderEntries();}}});
        Button add=button("＋ 記下一件好事",true,()->openEditor(null)); add.setTag("add-entry"); add.setEnabled(store.readable); toolbar.addView(add); space(content,16);
        list=column(); content.addView(list); renderEntries();
    }
    private void renderEntries() {
        if(list==null)return; list.removeAllViews(); List<JournalStore.Entry> daily=daily(); int shown=0;
        for(int i=0;i<daily.size();i++) {
            JournalStore.Entry entry=daily.get(i); if(!filter.isEmpty()&&!filter.equals(entry.category))continue; shown++;
            LinearLayout card=card(list), top=row(); card.addView(top);
            TextView category=text(String.format(Locale.ROOT,"%02d  %s",i+1,entry.category),13,true); top.addView(category,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=button("編輯",false,()->openEditor(entry)); edit.setTag("edit-"+entry.id); top.addView(edit);
            Button delete=button("刪除",false,()->{confirmation=new AlertDialog.Builder(this).setTitle("刪除好事？").setMessage("刪除後無法復原。").setPositiveButton("刪除",(dialog,which)->{List<JournalStore.Entry> next=store.entries();next.removeIf(item->item.id.equals(entry.id));save(next);}).setNegativeButton("取消",null).create();confirmation.show();}); delete.setTag("delete-"+entry.id); top.addView(delete);
            space(card,14); TextView body=text(entry.text,17,false); body.setTag("entry-text-"+entry.id); body.setTextIsSelectable(true); card.addView(body);
            if(!entry.reflection.isEmpty()){space(card,12);TextView reflection=text(entry.reflection,14,false);reflection.setTextColor(MUTED);card.addView(reflection);}
        }
        if(shown==0) {LinearLayout empty=card(list);empty.addView(text(daily.isEmpty()?"這天還沒有紀錄。":"這個分類還沒有紀錄。",15,false));}
    }
    private boolean save(List<JournalStore.Entry> next) {
        try { store.save(next); render(); return true; } catch(Exception error){message("儲存失敗，原有紀錄仍保留。請先備份並確認手機空間。");return false;}
    }
    private EditText input(LinearLayout parent,String label,String value,String hint,int limit,int lines,String tag) {
        parent.addView(text(label,15,true)); EditText field=new EditText(this); field.setText(value);field.setHint(hint);field.setTextColor(INK);field.setTextSize(16);field.setTag(tag);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);field.setMinLines(lines);field.setGravity(Gravity.TOP);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});parent.addView(field,new LinearLayout.LayoutParams(-1,-2));space(parent,16);return field;
    }
    void openEditor(JournalStore.Entry original) {
        LinearLayout fields=column();fields.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        final LocalDate[] date={original==null?selected:original.date};fields.addView(text("日期",15,true));
        Button dateButton=button(date[0].toString(),false,()->{});dateButton.setTag("entry-date");dateButton.setOnClickListener(v->pickDate(date[0],value->{date[0]=value;dateButton.setText(value.toString());}));fields.addView(dateButton);space(fields,16);
        EditText text=input(fields,"發生了什麼好事？",original==null?"":original.text,"例如：晚上吃到了我最喜歡的巧克力。",2000,3,"entry-text");
        fields.addView(text("分類",15,true));List<String> options=categories();options.add("新增");Spinner category=spinner(options);category.setTag("entry-category");fields.addView(category);category.setSelection(original==null?0:options.subList(0,options.size()-1).indexOf(original.category));space(fields,8);
        LinearLayout customBox=column();fields.addView(customBox);EditText custom=input(customBox,"新的分類名稱","","例如：美食時光",40,1,"custom-category");customBox.setVisibility(View.GONE);
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){customBox.setVisibility(position==options.size()-1?View.VISIBLE:View.GONE);}});
        EditText reflection=input(fields,"為什麼這件事讓你開心？（選填）",original==null?"":original.reflection,"也可以寫下讓這件事發生的原因。",2000,2,"entry-reflection");
        editor=new AlertDialog.Builder(this).setTitle(original==null?"記下一件好事":"編輯這件好事").setView(scroll).setPositiveButton("儲存",null).setNegativeButton("取消",null).create();
        editor.setOnShowListener(dialog->editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String body=JournalStore.trim(text.getText().toString()), name=category.getSelectedItemPosition()==options.size()-1?JournalStore.trim(custom.getText().toString()):options.get(category.getSelectedItemPosition());
            if(body.isEmpty()){text.setError("請寫下一件好事。");text.requestFocus();return;}if(name.isEmpty()){custom.setError("請輸入分類名稱。");custom.requestFocus();return;}
            JournalStore.Entry entry=new JournalStore.Entry(original==null?UUID.randomUUID().toString():original.id,date[0],body,JournalStore.trim(reflection.getText().toString()),name);
            List<JournalStore.Entry> next=store.entries();if(original==null)next.add(entry);else for(int i=0;i<next.size();i++)if(next.get(i).id.equals(original.id)){next.set(i,entry);break;}
            try {store.save(next);selected=date[0];month=YearMonth.from(selected);filter="";editor.dismiss();render();message("已儲存");}catch(Exception error){message("儲存失敗，請先備份並確認手機空間。");}
        }));editor.show();
    }
    private void renderBackup() {
        content.addView(text("備份與資料",21,true));space(content,16);LinearLayout info=card(content);
        info.addView(text("Android 安裝版",18,true));space(info,10);info.addView(text("這是原生 Android APP，不需要瀏覽器或網路。紀錄只存在這支手機，沒有自動同步。",15,false));space(info,12);
        info.addView(text("目前收藏 "+store.entries().size()+" 件好事。",15,true));space(info,12);info.addView(text("請定期匯出備份。解除安裝或清除 APP 資料會刪除紀錄；備份包含日記全文，請妥善保管。",14,false));space(info,16);
        Button export=button("匯出 JSON 備份",true,this::exportBackup);export.setTag("export-backup");export.setEnabled(store.readable);info.addView(export);space(info,14);
        Button restore=button("匯入 JSON 備份",false,this::importBackup);restore.setTag("import-backup");restore.setEnabled(store.readable);info.addView(restore);space(info,12);info.addView(text("可匯入網頁版備份。相同編號保留本機版本，不會覆蓋。",13,false));
        LinearLayout updates=card(content);updates.addView(text("更新方式",18,true));space(updates,10);updates.addView(text("下載新版 APK 覆蓋安裝即可。請保留舊版與紀錄，更新前先匯出備份。GitHub 網頁更新不會自動更新這個 APK。",14,false));
    }
    void exportBackup() {
        if(pendingBackup!=null){message("請先完成或取消目前的匯出。");return;}
        try {pendingBackup=store.backup();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"three-good-things-"+LocalDate.now()+".json");startActivityForResult(intent,EXPORT);}
        catch(Exception error){pendingBackup=null;message("無法開啟檔案儲存功能。");}
    }
    void importBackup() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
        try {startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException error){message("找不到手機的檔案選擇器。");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==EXPORT){String json=pendingBackup;pendingBackup=null;if(resultCode!=RESULT_OK||data==null||data.getData()==null){message("已取消匯出備份");return;}if(json==null){message("備份未完成，請重新匯出。");return;}Uri uri=data.getData();
            new Thread(()->{String result;try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException();output.write(json.getBytes(StandardCharsets.UTF_8));output.flush();result="備份已儲存";}catch(Exception error){result="備份儲存失敗，請重新選擇位置。";}String text=result;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message(text);});},"backup-export").start();
        }
        if(requestCode==IMPORT&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();
            new Thread(()->{try(InputStream input=getContentResolver().openInputStream(uri)){List<JournalStore.Entry> incoming=JournalStore.readBackup(JournalStore.readText(input));runOnUiThread(()->{if(isFinishing()||isDestroyed())return;try{List<JournalStore.Entry> next=JournalStore.merge(store.entries(),incoming);int added=next.size()-store.entries().size();confirmation=new AlertDialog.Builder(this).setTitle("匯入備份").setMessage("將新增 "+added+" 件好事，相同編號保留本機版本。").setPositiveButton("匯入",(dialog,which)->{if(save(next))message("已匯入 "+added+" 件好事");}).setNegativeButton("取消",null).create();confirmation.show();}catch(Exception error){message("無法合併備份，未變更任何紀錄。");}});}catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message("備份格式不正確或超過 20 MB，未變更任何紀錄。");});}},"backup-import").start();
        }
    }
}
