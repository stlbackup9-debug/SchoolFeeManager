# User Manual / အသုံးပြုနည်း လမ်းညွှန်

- [English](#english)
- [မြန်မာ](#မြန်မာ)

---

## English

### Screen layout
On a computer the menu is on the left; on a phone it is at the bottom. Pages: **Dashboard, New Entry, Records, Reports, History, Settings**.
At the bottom of the side menu (top bar on a phone) are the **language button** (English ⇄ မြန်မာ), the **theme button** (☀ / ☾) and the save status (● Saved). Every change is saved automatically; there is no Save-to-disk step.

### Dashboard
- **Today's Records / Today's Total Amount / 2D Total / 3D Total**: figures for today. The 2D card also shows the Morning and Evening split.
- **Total Transactions**: all records in the database.
- **Daily / Weekly / Monthly Summary**: today, this week (Monday to today) and this month.
- **Last 14 days**: 2D (blue) and 3D (orange) per day. Hover over or tap a bar to see the amounts.
- **Top numbers**: today's top 2D numbers and this month's top 3D numbers.
- **+2D / +3D** buttons open the entry screen.

### Entering records (New Entry)
1. Choose **2D** or **3D**.
2. Check the **Date** (today by default). For 2D choose **Morning** or **Evening**. The session is preselected from the clock: Morning until 12:01 PM.
3. Type the **Number(s)**:
   - 2D: `25`. Several at once: `25 52 07` (separate them with spaces or commas).
   - 3D: `123`, or `123 456`.
   - Tick **R** to add the reverse of each 2D number (25 → 25 and 52). For 3D, the tick adds every arrangement (123 → 123 132 213 231 312 321).
4. Type the **Amount** in Kyat, or tap a quick amount (500, 1,000 …). It must be a whole number from 1 to 100,000,000.
5. Optional: **Customer Name** (earlier names are suggested) and **Remark**.
6. The preview shows the numbers and the total (e.g. *4 number(s) × 1,000 = 4,000 Ks*). Press **Save record(s)** or Enter.

After saving, the number box is cleared and the date, session, amount and customer are kept, so you can type the next number straight away. The right-hand panel lists the entries for the chosen date (and session) with their total.

**Duplicate warning**: if a record with the same date, session, number, amount and customer already exists, the app asks before saving. Choose *Cancel* or *Save anyway*. You can turn this warning off in Settings.

**Errors** are shown in red under the field: e.g. *2D number must be 2 digits (00–99)*. Nothing is saved until every field is correct.

### Records: search, filter, edit, delete
- **Search number**: type `25` to show records with exactly 25. Tick **Contains** to also match 125, 250…
- Filter by **Type** (All/2D/3D), **Session**, **From/To** date and **Customer**. Change the sort order with **Sort**.
- The header shows how many records match and their total. Below it are the 2D, 3D, Morning and Evening totals.
- ✎ edits a record; 🗑 deletes it (after confirmation). Tick several rows to use **Delete selected**.
- **⬇ Excel** exports all matching records (not only the current page).

### History
Lists every add, edit and delete with the time and details. A deleted record can be brought back with **↺ Restore**.

### Reports
Choose a report and its options. The report appears below and can be saved with **Excel** or **PDF**, or printed with **Print**.

| Report | Contents |
|---|---|
| Daily report | One date: summary (2D Morning, 2D Evening, 2D total, 3D total, grand total), totals by number for each session and for 3D, and the full list of entries. Use it for daily printing. |
| Total by number | Date range, 2D/3D, optional session. 2D also gets a 00–99 grid (row = first digit, column = second digit). |
| Total by date | Each day: count, 2D Morning, 2D Evening, 2D total, 3D total, total. |
| Total by session | Morning vs Evening totals and share, plus a per-day breakdown. |
| Monthly totals | All 12 months of a year. |
| Customer summary | Per customer: count, 2D, 3D, total, first and last date. |

Each Excel export has one sheet per report section, with real numbers that you can sum in Excel. 2D/3D numbers keep their leading zeros (07, 005).

### Settings
- **Language**, **Theme** (Light / Dark / System), **Business name** (printed on reports), and the **duplicate warning** on/off.
- **Backup database**: saves the whole database as `2d3d-backup-YYYYMMDD-HHMM.sqlite`. Keep copies on another device.
- **Restore from file**: choose a `.sqlite` backup. The file is checked first, and the app shows how many records it contains and asks you to confirm. The current data is replaced. A safety copy is kept under *Automatic daily snapshots*.
- **Automatic daily snapshots**: a copy from each of the last 7 days the app was opened (plus safety copies made before a restore or delete-all). Click **Restore** to go back to one.
- **Load sample data**: adds about 45 days of test records.
- **Delete ALL records**: type `DELETE` to confirm. A snapshot is taken first.

---

## မြန်မာ

### မျက်နှာပြင် ဖွဲ့စည်းပုံ
ကွန်ပျူတာတွင် မီနူးသည် ဘယ်ဘက်တွင် ရှိပြီး ဖုန်းတွင် အောက်ခြေ၌ ရှိသည်။ စာမျက်နှာများ: **ပင်မစာမျက်နှာ၊ စာရင်းသွင်းရန်၊ မှတ်တမ်းများ၊ အစီရင်ခံစာ၊ ပြင်ဆင်မှုမှတ်တမ်း၊ ဆက်တင်**။
**ဘာသာစကား ခလုတ်** (English ⇄ မြန်မာ)၊ **အလင်း/အမှောင် ခလုတ်** (☀ / ☾) နှင့် သိမ်းဆည်းမှု အခြေအနေ (● သိမ်းပြီး) ကို မီနူးအောက်ခြေ (ဖုန်းတွင် ထိပ်ဘား) တွင် တွေ့နိုင်သည်။ ပြောင်းလဲမှုတိုင်းကို အလိုအလျောက် သိမ်းဆည်းသည်။

### ပင်မစာမျက်နှာ
- **ယနေ့ စာရင်းအရေအတွက် / ယနေ့ စုစုပေါင်းငွေ / 2D စုစုပေါင်း / 3D စုစုပေါင်း**: ယနေ့အတွက် ဖြစ်သည်။ 2D တွင် မနက်/ညနေ ခွဲပြသည်။
- **စာရင်းအားလုံး**: ဒေတာဘေ့စ်ရှိ စာရင်း စုစုပေါင်း။
- **နေ့စဉ် / အပတ်စဉ် / လစဉ် အနှစ်ချုပ်**: ယနေ့၊ ယခုအပတ် (တနင်္လာမှ)၊ ယခုလ။
- **နောက်ဆုံး ၁၄ ရက်** ဇယား (2D အပြာ၊ 3D လိမ္မော်)။ ဘားကို နှိပ်လျှင် ငွေပမာဏ ပြသည်။
- **+2D / +3D** ခလုတ်ဖြင့် စာရင်းသွင်းရန် စာမျက်နှာကို ဖွင့်နိုင်သည်။

### စာရင်းသွင်းခြင်း
1. **2D** သို့မဟုတ် **3D** ရွေးပါ။
2. **ရက်စွဲ** စစ်ပါ (ယနေ့ ဖြစ်နေမည်)။ 2D အတွက် **မနက်** သို့မဟုတ် **ညနေ** ရွေးပါ။
3. **ဂဏန်း** ရိုက်ပါ:
   - 2D: `25`။ တစ်ခါတည်း အများ: `25 52 07` (space သို့မဟုတ် ကော်မာဖြင့် ခြားပါ)။
   - 3D: `123` သို့မဟုတ် `123 456`။
   - **R** ကို အမှန်ခြစ်လျှင် 2D အပြန်ဂဏန်း (25 → 25 နှင့် 52) ပါ ထည့်မည်။ 3D တွင် ပတ်လည် အားလုံး (123 → ဂဏန်း ၆ လုံး) ထည့်မည်။
   - မြန်မာဂဏန်း (၂၅) ဖြင့်လည်း ရိုက်နိုင်သည်။
4. **ငွေပမာဏ** (ကျပ်) ရိုက်ပါ သို့မဟုတ် အမြန်ခလုတ် (500၊ 1,000 …) နှိပ်ပါ။ 1 မှ 100,000,000 အတွင်း ကိန်းပြည့် ဖြစ်ရမည်။
5. **ဝယ်သူအမည်** နှင့် **မှတ်ချက်** (မဖြစ်မနေ မဟုတ်ပါ)။
6. အစမ်းကြည့်တွင် ဂဏန်းများ နှင့် စုစုပေါင်းကို ပြသည်။ **စာရင်း သိမ်းမည်** (သို့မဟုတ် Enter) နှိပ်ပါ။

သိမ်းပြီးနောက် ဂဏန်းအကွက်သာ ရှင်းသွားပြီး ရက်စွဲ၊ အချိန်ပိုင်း၊ ငွေ၊ ဝယ်သူ ကျန်နေသဖြင့် နောက်ဂဏန်းကို ချက်ချင်း ရိုက်နိုင်သည်။

**ထပ်နေသော စာရင်း သတိပေးချက်**: ရက်စွဲ၊ အချိန်ပိုင်း၊ ဂဏန်း၊ ငွေ၊ ဝယ်သူ တူသော စာရင်း ရှိပြီးသား ဖြစ်လျှင် မသိမ်းမီ မေးမည်။ *မလုပ်တော့ပါ* သို့မဟုတ် *ဆက်၍ သိမ်းမည်* ရွေးပါ။

### မှတ်တမ်းများ
- **ဂဏန်းဖြင့် ရှာရန်**: `25` ရိုက်လျှင် 25 အတိအကျ ကိုသာ ပြမည်။ **ပါဝင်သော** ကို ခြစ်လျှင် 125၊ 250 စသည်တို့လည်း ပါမည်။
- **အမျိုးအစား၊ အချိန်ပိုင်း၊ ရက်စွဲ (မှ/ထိ)၊ ဝယ်သူ** ဖြင့် စစ်ထုတ်နိုင်သည်။
- ✎ ပြင်ရန်၊ 🗑 ဖျက်ရန်။ အများအပြား ရွေးပြီး တစ်ပြိုင်နက် ဖျက်နိုင်သည်။
- **⬇ Excel** ဖြင့် ကိုက်ညီသော စာရင်းအားလုံးကို Excel ဖိုင် ထုတ်နိုင်သည်။

### ပြင်ဆင်မှု မှတ်တမ်း
ထည့်/ပြင်/ဖျက် မှုတိုင်းကို အချိန်နှင့်တကွ မှတ်ထားသည်။ ဖျက်ထားသော စာရင်းကို **↺ ပြန်ယူမည်** ဖြင့် ပြန်ရနိုင်သည်။

### အစီရင်ခံစာများ
အစီရင်ခံစာ အမျိုးအစား ရွေးပြီး **Excel**၊ **PDF** သို့မဟုတ် **ပုံနှိပ်မည်** ကို နှိပ်ပါ။
- **နေ့စဉ် အစီရင်ခံစာ**: ရက်တစ်ရက်၏ အနှစ်ချုပ်၊ မနက်/ညနေ/3D ဂဏန်းအလိုက် စုစုပေါင်း နှင့် စာရင်းအပြည့်အစုံ (နေ့စဉ် ပုံနှိပ်ရန်)။
- **ဂဏန်းအလိုက် စုစုပေါင်း**: 2D အတွက် 00–99 ဇယားပါ ပြသည် (အတန်း = ပထမဂဏန်း၊ ကော်လံ = ဒုတိယဂဏန်း)။
- **ရက်စွဲအလိုက်**၊ **အချိန်ပိုင်းအလိုက်**၊ **လစဉ်**၊ **ဝယ်သူ အနှစ်ချုပ်**။

### ဆက်တင်များ
- **ဘာသာစကား၊ အရောင်ပုံစံ၊ လုပ်ငန်းအမည်၊ ထပ်နေသော စာရင်း သတိပေးချက်**။
- **ဒေတာဘေ့စ် အရန်သိမ်းမည်**: `.sqlite` ဖိုင်အဖြစ် သိမ်းမည်။ Drive/Telegram သို့မဟုတ် အခြားစက်တွင် မကြာခဏ သိမ်းထားပါ။
- **ဖိုင်မှ ပြန်ထည့်မည်**: အရန်ဖိုင်ကို စစ်ဆေးပြီး စာရင်းအရေအတွက်ကို ပြကာ အတည်ပြုခိုင်းမည်။ လက်ရှိဒေတာ အစားထိုးခံရမည် (ဘေးကင်းရေး မိတ္တူ အရင်ယူမည်)။
- **အလိုအလျောက် နေ့စဉ် မိတ္တူများ**: နောက်ဆုံး ၇ ရက်။
- **နမူနာဒေတာ ထည့်မည်** / **စာရင်း အားလုံး ဖျက်မည်** (`DELETE` ဟု ရိုက်၍ အတည်ပြုရမည်)။

> **သတိ**: App ကို ဖြုတ်ခြင်း (Uninstall) သို့မဟုတ် "Clear data" လုပ်လျှင် ဒေတာ ပျက်မည်။ အရန်ဖိုင် ပုံမှန် သိမ်းပါ။
