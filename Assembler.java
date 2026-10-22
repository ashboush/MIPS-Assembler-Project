import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Assembler {
    static final int TEXT_BASE = 0x00400000;
    static final int DATA_BASE = 0x10010000;
    static final int MAGIC = 0x4D484331; // "MHC1"

    enum Section { NONE, TEXT, DATA }
    record Line(int number, String text, Section section) {}

    static final Map<String,Integer> REG = new HashMap<>();
    static {
        String[] names = {"zero","at","v0","v1","a0","a1","a2","a3","t0","t1","t2","t3","t4","t5","t6","t7",
                "s0","s1","s2","s3","s4","s5","s6","s7","t8","t9","k0","k1","gp","sp","fp","ra"};
        for (int i=0;i<32;i++) { REG.put("$"+names[i], i); REG.put("$"+i, i); }
        REG.put("$s8",30);
    }

    static Map<String,Integer> labels = new LinkedHashMap<>();
    static List<Line> lines = new ArrayList<>();
    static ByteArrayOutputStream data = new ByteArrayOutputStream();

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java Assembler <input.asm> <output.mhc-ascii> <output.mhc-bin>");
            System.exit(1);
        }
        try {
            parseAndFirstPass(Path.of(args[0]));
            byte[] text = secondPass();
            byte[] dataBytes = data.toByteArray();
            byte[] container = buildContainer(text, dataBytes);
            Files.write(Path.of(args[2]), container);
            writeAscii(Path.of(args[1]), container);
            System.out.printf("Assembled %d text bytes and %d data bytes.%n", text.length, dataBytes.length);
            System.out.printf("Wrote %s and %s%n", args[1], args[2]);
        } catch (AsmException e) {
            System.err.println("Assembler error: " + e.getMessage());
            System.exit(2);
        }
    }

    static void parseAndFirstPass(Path input) throws IOException, AsmException {
        List<String> raw = Files.readAllLines(input);
        Section section = Section.NONE;
        int textPc = TEXT_BASE, dataPc = DATA_BASE;
        for (int i=0;i<raw.size();i++) {
            String s = stripComment(raw.get(i)).trim();
            if (s.isEmpty()) continue;
            if (s.equals(".text")) { section=Section.TEXT; continue; }
            if (s.equals(".data")) { section=Section.DATA; continue; }
            if (s.startsWith(".globl")) continue;

            while (s.contains(":")) {
                int c=s.indexOf(':');
                String lab=s.substring(0,c).trim();
                if (!lab.matches("[A-Za-z_][A-Za-z0-9_]*")) fail(i+1,"invalid label: "+lab);
                if (labels.containsKey(lab)) fail(i+1,"duplicate label: "+lab);
                labels.put(lab, section==Section.TEXT ? textPc : dataPc);
                s=s.substring(c+1).trim();
                if (s.isEmpty()) break;
            }
            if (s.isEmpty()) continue;
            if (section==Section.NONE) fail(i+1,"content appears before .text or .data");
            lines.add(new Line(i+1,s,section));
            if (section==Section.DATA) {
                byte[] b=encodeDataDirective(s,i+1);
                data.writeBytes(b); dataPc += b.length;
            } else {
                textPc += expansionWords(s,i+1)*4;
            }
        }
    }

    static byte[] secondPass() throws AsmException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int pc=TEXT_BASE;
        for (Line line: lines) {
            if (line.section()!=Section.TEXT) continue;
            List<Integer> words=encodeInstruction(line.text(),pc,line.number());
            for(int w:words){ writeWord(out,w); pc+=4; }
        }
        return out.toByteArray();
    }

    static int expansionWords(String s,int line) throws AsmException {
        String op=firstToken(s).toLowerCase();
        return switch(op) {
            case "rem" -> 3; // addiu $at,$zero,imm ; div rs,$at ; mfhi rd (FizzBuzz uses immediate divisors)
            case "la" -> 2;
            case "ble" -> 2;
            case "li","move","addi","addiu","addu","add","sub","beq","bne","j","syscall","lui","ori","div","mfhi","slt","nop" -> 1;
            default -> throw new AsmException("line "+line+": unsupported instruction: "+op);
        };
    }

    static List<Integer> encodeInstruction(String s,int pc,int line) throws AsmException {
        String op=firstToken(s).toLowerCase();
        String rest=s.substring(firstToken(s).length()).trim();
        String[] a=splitArgs(rest);
        List<Integer> w=new ArrayList<>();
        switch(op) {
            case "li" -> { need(a,2,line); int rt=reg(a[0],line), imm=num(a[1],line); checkSigned16(imm,line); w.add(iType(0x09,0,rt,imm)); }
            case "move" -> { need(a,2,line); w.add(rType(reg(a[1],line),0,reg(a[0],line),0,0x21)); }
            case "rem" -> {
                need(a,3,line); int rd=reg(a[0],line), rs=reg(a[1],line), imm=num(a[2],line); checkSigned16(imm,line);
                w.add(iType(0x09,0,1,imm)); w.add(rType(rs,1,0,0,0x1A)); w.add(rType(0,0,rd,0,0x10));
            }
            case "la" -> {
                need(a,2,line); int rt=reg(a[0],line), addr=label(a[1],line);
                w.add(iType(0x0F,0,rt,(addr>>>16)&0xffff)); w.add(iType(0x0D,rt,rt,addr&0xffff));
            }
            case "ble" -> {
                need(a,3,line); int rs=reg(a[0],line), rt=reg(a[1],line), target=label(a[2],line);
                w.add(rType(rt,rs,1,0,0x2A));
                int off=(target-(pc+8))/4; checkSigned16(off,line); w.add(iType(0x04,1,0,off));
            }
            case "addi" -> { need(a,3,line); int imm=num(a[2],line); checkSigned16(imm,line); w.add(iType(0x08,reg(a[1],line),reg(a[0],line),imm)); }
            case "addiu" -> { need(a,3,line); int imm=num(a[2],line); checkSigned16(imm,line); w.add(iType(0x09,reg(a[1],line),reg(a[0],line),imm)); }
            case "ori" -> { need(a,3,line); int imm=num(a[2],line); checkUnsigned16(imm,line); w.add(iType(0x0D,reg(a[1],line),reg(a[0],line),imm)); }
            case "lui" -> { need(a,2,line); int imm=num(a[1],line); checkUnsigned16(imm,line); w.add(iType(0x0F,0,reg(a[0],line),imm)); }
            case "beq","bne" -> { need(a,3,line); int target=label(a[2],line), off=(target-(pc+4))/4; checkSigned16(off,line); w.add(iType(op.equals("beq")?4:5,reg(a[0],line),reg(a[1],line),off)); }
            case "j" -> { need(a,1,line); int target=label(a[0],line); w.add((0x02<<26)|((target>>>2)&0x03ffffff)); }
            case "add","addu","sub","slt" -> { need(a,3,line); int f=switch(op){case "add"->0x20;case "addu"->0x21;case "sub"->0x22;default->0x2A;}; w.add(rType(reg(a[1],line),reg(a[2],line),reg(a[0],line),0,f)); }
            case "div" -> { need(a,2,line); w.add(rType(reg(a[0],line),reg(a[1],line),0,0,0x1A)); }
            case "mfhi" -> { need(a,1,line); w.add(rType(0,0,reg(a[0],line),0,0x10)); }
            case "syscall" -> { need(a,0,line); w.add(0x0000000C); }
            case "nop" -> { need(a,0,line); w.add(0); }
            default -> throw new AsmException("line "+line+": unsupported instruction: "+op);
        }
        return w;
    }

    static byte[] encodeDataDirective(String s,int line) throws AsmException {
        if (s.startsWith(".asciiz")) {
            String q=s.substring(7).trim();
            if(q.length()<2 || q.charAt(0)!='"' || q.charAt(q.length()-1)!='"') fail(line,".asciiz requires a quoted string");
            String val=unescape(q.substring(1,q.length()-1),line);
            byte[] raw=val.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return Arrays.copyOf(raw,raw.length+1);
        }
        if (s.startsWith(".byte")) {
            String[] vals=splitArgs(s.substring(5).trim()); ByteArrayOutputStream b=new ByteArrayOutputStream();
            for(String v:vals){ int n=num(v,line); if(n < -128 || n > 255) fail(line,".byte value out of range: "+n); b.write(n&0xff); }
            return b.toByteArray();
        }
        throw new AsmException("line "+line+": unsupported data directive: "+firstToken(s));
    }

    static byte[] buildContainer(byte[] text,byte[] dat) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        writeWord(b,MAGIC); writeWord(b,TEXT_BASE); writeWord(b,text.length); writeWord(b,DATA_BASE); writeWord(b,dat.length); writeWord(b,0); // flags/reserved
        b.write(text); b.write(dat); return b.toByteArray();
    }
    static void writeAscii(Path p,byte[] bytes) throws IOException {
        try(BufferedWriter bw=Files.newBufferedWriter(p)){
            for(int i=0;i<bytes.length;i++){
                bw.write(String.format("%8s",Integer.toBinaryString(bytes[i]&0xff)).replace(' ','0'));
                if((i+1)%4==0) bw.newLine();
            }
        }
    }
    static void writeWord(OutputStream o,int w){ 
        try{
            o.write((w>>>24)&255);o.write((w>>>16)&255);o.write((w>>>8)&255);o.write(w&255);}
        catch(IOException e){
            throw new UncheckedIOException(e);} 
        }
    static int rType(int rs,int rt,int rd,int sh,int f){
        return (rs<<21)|(rt<<16)|(rd<<11)|(sh<<6)|f;
    }
    static int iType(int op,int rs,int rt,int imm){
        return (op<<26)|(rs<<21)|(rt<<16)|(imm&0xffff);
    }
    static String stripComment(String s){
        int x=s.indexOf('#');
        return x>=0?s.substring(0,x):s;
    }
    static String firstToken(String s){
        String t=s.trim();
        int i=0;
        while(i<t.length()&&!Character.isWhitespace(t.charAt(i)))i++;
        return t.substring(0,i);
    }
    static String[] splitArgs(String s){
        if(s.isBlank())return new String[0];
        return Arrays.stream(s.split(",")).map(String::trim).toArray(String[]::new);
    }
    static void need(String[] a,int n,int line)throws AsmException{
        if(a.length!=n)fail(line,"expected "+n+" operand(s), found "+a.length);
    }
    static int reg(String s,int line)throws AsmException{
        Integer r=REG.get(s.toLowerCase());
        if(r==null)fail(line,"unknown register: "+s);
        return r;
    }
    static int label(String s,int line)throws AsmException{
        Integer v=labels.get(s);
        if(v==null)fail(line,"unknown label: "+s);
        return v;
    }
    static int num(String s,int line)throws AsmException{
        try{
            return Integer.decode(s);}
        catch(Exception e){
            fail(line,"invalid number: "+s);
        return 0;
        }
    }
    static void checkSigned16(int n,int line)throws AsmException{
        if(n<-32768||n>32767)
            fail(line,"signed 16-bit immediate out of range: "+n);
    }
    static void checkUnsigned16(int n,int line)throws AsmException{
        if(n<0||n>65535)
            fail(line,"unsigned 16-bit immediate out of range: "+n);
    }
    static String unescape(String s,int line)throws AsmException{
        StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c!='\\'){b.append(c);
                continue;
            }
            if(++i>=s.length())
                fail(line,"bad string escape");
            char e=s.charAt(i);
            b.append(switch(e){case 'n'->'\n';case 't'->'\t';case 'r'->'\r';case '\\'->'\\';case '"'->'"';default->e;});
        }return b.toString();
    }
    static void fail(int line,String m)throws AsmException{
        throw new AsmException("line "+line+": "+m);
    }
    static class AsmException extends Exception{
        AsmException(String m){super(m);}
    }
}
