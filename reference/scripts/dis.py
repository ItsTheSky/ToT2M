import sys,capstone,json
so=open(sys.argv[1],'rb').read(); off=int(sys.argv[2],16); n=int(sys.argv[3])
sj=json.load(open(sys.argv[4])); names={m['Address']:m['Name'] for m in sj['ScriptMethod']}
md=capstone.Cs(capstone.CS_ARCH_ARM64,capstone.CS_MODE_ARM)
for i,ins in enumerate(md.disasm(so[off:off+n*4],off)):
    extra=''
    if ins.mnemonic=='bl':
        t=int(ins.op_str.lstrip('#'),16); extra=names.get(t,'')
    print(hex(ins.address),ins.mnemonic,ins.op_str,extra)
    if ins.mnemonic=='ret' and i>8: break
