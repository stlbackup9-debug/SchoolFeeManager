import openpyxl
def load(f):
    wb=openpyxl.load_workbook(f,data_only=True); return wb['Student_Register'], wb['Payment_Schedule']
app=[l.rstrip('\n').split('\t') for l in open('app_values.tsv') if not l.startswith('errors')]
cols=['Training','Instrument','Book','Hostel','Laundry','Entrance','Gross','DiscAmt','Net','Paid','Balance']
xcols=[7,8,9,10,11,12,13,15,16,18,19]
def n(v): return 0 if v in (None,'') else v
for label,f in [('FIXED formulas (approved)','recalc/filled_fixed.xlsx'),('ORIGINAL formulas','recalc/filled_original.xlsx')]:
    reg,ps=load(f); exact=0; within=0; diffs={}
    sched_ok=0; sched_rounding=0
    for i,row in enumerate(app):
        r=i+3; assert reg.cell(r,2).value==row[0]
        for c,xc,av in zip(cols,xcols,row[1:12]):
            ev=n(reg.cell(r,xc).value); av=int(av)
            if ev==av: exact+=1
            elif abs(ev-av)<=0.5: within+=1; diffs.setdefault(c+' (sub-kyat rounding)',0); diffs[c+' (sub-kyat rounding)']+=1
            else: diffs.setdefault(c,0); diffs[c]+=1
        parts=[int(x) for x in row[12].split('/')]
        term=reg.cell(r,17).value; first=6 if term=='3-Inst' else 9
        xl=[n(ps.cell(r,first+k).value) for k in range(len(parts))]
        if xl[:-1]==parts[:-1] and abs(sum(parts)-int(row[9]))==0:
            if xl[-1]==parts[-1]: sched_ok+=1
            else: sched_rounding+=1
    print(f'== {label}: {len(app)} students x 11 columns = {len(app)*11} cells: exact {exact}, within 0.5 kyat {within}, different {sum(v for k,v in diffs.items() if "sub-kyat" not in k)}')
    print('   differences by column:',diffs or 'none')
    print(f'   schedules: {sched_ok} identical to Excel, {sched_rounding} identical except last part absorbs Excel rounding gap')
