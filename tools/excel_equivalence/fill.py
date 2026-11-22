import openpyxl, datetime, random, sys
random.seed(7)
classes=['KG']+[f'G-{i}' for i in range(1,13)]
discs=[0,0.05,0.1,0.125,0.333]
names=[]
def build(fixed, out):
    wb=openpyxl.load_workbook(sys.argv[1])
    ws=wb['Student_Register']
    for i in range(50):
        r=i+3; name=f'Student {i+1:02d}'
        if not fixed: names.append(name)
        ws.cell(r,2,name); ws.cell(r,3,classes[i%13]); ws.cell(r,4,['Day','Hostel'][i%2])
        ws.cell(r,5,['New','Old'][(i//2)%2]); ws.cell(r,6,['Myanmar','International'][(i//4)%2])
        ws.cell(r,14,discs[i%5]); ws.cell(r,17,['3-Inst','Monthly'][(i//3)%2])
        if fixed:
            ws.cell(r,8).value=ws.cell(r,8).value.replace('Fee_Rates!$G$7','Fee_Rates!$G$6')
            ws.cell(r,10).value=ws.cell(r,10).value.replace('Fee_Rates!$A$23:$B$35','Fee_Rates!$A$22:$B$34')
    cc=wb['Cash_Collection']
    rnd=random.Random(11)
    for j in range(60):
        r=j+3; k=rnd.randrange(50)
        cc.cell(r,2,f'Student {k+1:02d}'); cc.cell(r,3,classes[k%13])
        cc.cell(r,4,datetime.datetime(2026,6,1)+datetime.timedelta(days=rnd.randrange(150)))
        cc.cell(r,5,rnd.choice([50000,100000,150000,183333,250000,333333,12345]))
        cc.cell(r,6,f'OLD-{j+1:04d}'); cc.cell(r,7,rnd.choice(['Inst-1','Inst-2','M-1','M-2','Installment 3','Month 5','Other']))
    wb.save(out)
build(False,'filled_original.xlsx'); build(True,'filled_fixed.xlsx')
