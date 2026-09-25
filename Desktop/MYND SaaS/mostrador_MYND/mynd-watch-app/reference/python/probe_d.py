from jlface import *
from PIL import Image, ImageDraw, ImageFont
T=open('xfer1.bin','rb').read()
F=ImageFont.truetype('fonts/oxanium-800.ttf',18); L=ImageFont.truetype('fonts/space-mono-700.ttf',12)
def spr(k):
    im=Image.new('RGBA',(40,24),(40,40,40,255)); d=ImageDraw.Draw(im)
    d.text((20,12),str(k),font=F,fill=(255,230,0,255),anchor='mm'); return encode_sprite(im)
sprites=[spr(k) for k in range(31)]
def probe(types,name):
    f=parse(T); els=[]
    bg=Image.new('RGB',(240,296)); d=ImageDraw.Draw(bg)
    d.text((6,4),name+'  (imagens)',font=L,fill=(0,212,245))
    for i,t in enumerate(types):
        x=6+(i%2)*117; y=24+(i//2)*54
        d.rectangle([x,y,x+112,y+50],outline=(40,70,80)); d.text((x+4,y+17),f'{t:02d}',font=L,fill=(0,212,245))
        els.append(dict(type=t,x=x+40,y=y+13,z=0,imgs=sprites))
    f['elements']=els
    space=PREV_OFF-(TABLE+ENTRY*len(els)); f['tail']=bytes(space)
    head=bytearray(f['head']); head[0x8C:0xB4]=bytes(0x28); head[0x8C]=1
    for i in range(len(els)): head[0x8D+i]=6
    f['head']=head; f['bg']=encode_rgb565(bg); f['preview']=encode_rgb565(bg.resize((156,214)))
    b=build(f); open(f'out/{name}.bin','wb').write(b); bg.save(f'out/{name}_layout.png')
    g=parse(b); assert [e['type'] for e in g['elements']]==types
    print(name,len(b),types)
probe([0,5,7,12,13,14,15,16,17,18],'sonda_D1')
probe([19,20,21,22,25,26,27,28,29,30],'sonda_D2')
probe([32,33,34,35,36,37,38,39,40,41],'sonda_D3')
