import struct
from jlface import *
from PIL import Image, ImageDraw, ImageFont
T = open('xfer1.bin','rb').read()
def rec(tid,x,y,w,color=0xFFFF,b10=1,b11=1,s=b'1000200000'):
    r=bytearray(32); r[0]=5; r[1]=tid; struct.pack_into('<HH',r,2,x,y); r[6]=w; r[7]=0xff
    struct.pack_into('>H',r,8,color); r[10]=b10; r[11]=b11; r[12:12+len(s)]=s; return bytes(r)
assert rec(0x0d,40,21,56,0xfaca)==T[0x564:0x584]
items=[  # (label, tid, w, color, b10, b11, s)
 ('05',5,100,0xFFFF,1,1,b'1000200000'),
 ('30',30,100,0xFFFF,1,1,b'1000200000'),
 ('16',16,100,0xFFFF,1,1,b'1000200000'),
 ('22',22,200,0xFFFF,1,1,b'1000200000'),
 ('19',19,110,0xFFFF,1,1,b'MYND OK'),
 ('cy',14,100,0x06BE,1,1,b'1000200000'),
]
for b10 in range(0,5):
    for b11 in range(0,2):
        items.append((f'{b10}{b11}',14,100,0xFFFF,b10,b11,b'1000200000'))
f=parse(T)
m=[e for e in f['elements'] if e['type']==4][0]
f['elements']=[dict(m,x=210,y=2,imgs=[f['res'][o] for o in m['offs']])]
bg=Image.new('RGB',(240,296)); d=ImageDraw.Draw(bg)
fnt=ImageFont.truetype('fonts/space-mono-700.ttf',11)
d.text((6,6),'sonda_C',font=ImageFont.truetype('fonts/space-mono-700.ttf',13),fill=(0,212,245))
recs=b''
for k,(lab,tid,w,col,b10,b11,s) in enumerate(items):
    if k<6: x,y,wid=4,26+k*26,232     # linhas largas
    else:
        kk=k-6; x=4+(kk%2)*118; y=26+6*26+(kk//2)*21; wid=114
    d.rectangle([x,y,x+wid,y+(24 if k<6 else 19)],outline=(40,60,70))
    d.text((x+3,y+4),lab,font=fnt,fill=(0,212,245))
    recs+=rec(tid,x+24,y,min(w,wid-26),col,b10,b11,s)
space=PREV_OFF-(TABLE+ENTRY); f['tail']=recs+bytes(space-len(recs))
head=bytearray(f['head']); head[0x8C:0xB4]=bytes(0x28); head[0x8C]=1; head[0x8D]=6
for i in range(len(items)): head[0x8E+i]=5
f['head']=head; f['bg']=encode_rgb565(bg); f['preview']=encode_rgb565(bg.resize((156,214)))
b=build(f); open('out/sonda_C.bin','wb').write(b); bg.save('out/sonda_C_layout.png'); print(len(items), len(b))
