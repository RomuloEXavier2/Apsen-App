import struct
from jlface import *
from PIL import Image, ImageDraw, ImageFont
T = open('xfer1.bin','rb').read()
tpl = parse(T)
REC_TAIL = T[0x564+12:0x564+32]          # "1000200000" + zeros (copiado do modelo)
def text_rec(tid, x, y, w=44, color=0xFFFF):
    r = bytearray(32)
    r[0]=5; r[1]=tid; struct.pack_into('<HH',r,2,x,y); r[6]=w; r[7]=0xff
    struct.pack_into('>H',r,8,color); r[10]=1; r[11]=1
    r[12:32]=REC_TAIL
    return bytes(r)
assert text_rec(0x0d,40,21,56,0xfaca)==T[0x564:0x584], 'registro não bate com o original'
assert text_rec(0x12,183,20,44,0xfaca)==T[0x584:0x5a4]

def probe(ids, name):
    f = parse(T)
    minute = [e for e in f['elements'] if e['type']==4][0]
    minute = dict(minute, x=196, y=2, imgs=[f['res'][o] for o in minute['offs']])
    f['elements'] = [minute]
    recs=b''; cells=[]
    for k,tid in enumerate(ids):
        col,row=k%3,k//3
        x=4+col*80; y=40+row*21
        recs+=text_rec(tid, x+26, y, 50)
        cells.append((tid,x,y))
    tail_space = PREV_OFF-(TABLE+ENTRY)
    assert len(recs)<=tail_space
    f['tail']=recs+bytes(tail_space-len(recs))
    head=bytearray(f['head']); head[0x8C:0xB4]=bytes(0x28); head[0x8C]=1; head[0x8D]=6
    for i in range(len(ids)): head[0x8E+i]=5
    assert 0x8E+len(ids)<=0xB4
    f['head']=head
    bg=Image.new('RGB',(240,296),(0,0,0)); d=ImageDraw.Draw(bg)
    fnt=ImageFont.truetype('fonts/space-mono-700.ttf',11); big=ImageFont.truetype('fonts/space-mono-700.ttf',13)
    d.text((6,6),name,font=big,fill=(0,212,245)); d.text((150,8),'min:',font=fnt,fill=(120,120,120))
    for tid,x,y in cells:
        d.rectangle([x,y,x+76,y+19],outline=(40,60,70))
        d.text((x+3,y+3),f'{tid:02d}',font=fnt,fill=(0,212,245))
    f['bg']=encode_rgb565(bg)
    f['preview']=encode_rgb565(bg.resize((156,214)))
    b=build(f); open(f'out/{name}.bin','wb').write(b); bg.save(f'out/{name}_layout.png')
    # re-parse check
    g=parse(b); assert g['tail'][:len(recs)]==recs and len(g['elements'])==1
    print(name,len(b),'ids',ids[0],'..',ids[-1])
probe(list(range(0,36)),'sonda_A')
probe(list(range(36,72)),'sonda_B')
