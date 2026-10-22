import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Disassembler {
    static final int MAGIC=0x4D484331;
    static final String[] R={"$zero","$at","$v0","$v1","$a0","$a1","$a2","$a3","$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7","$s0","$s1","$s2","$s3","$s4","$s5","$s6","$s7","$t8","$t9","$k0","$k1","$gp","$sp","$fp","$ra"};
    record Image(int textBase, byte[] text, int dataBase, byte[] data) {}

    public static void main(String[] args) throws Exception {
        if(args.length!=2){System.err.println("Usage: java Disassembler <input.mhc-ascii|input.mhc-bin> <output.asm>");System.exit(1);}
        try {
            byte[] raw=Files.readAllBytes(Path.of(args[0]));
            byte[] container=isAsciiBits(raw)?decodeAscii(raw):raw;
            Image im=parse(container);
            Files.writeString(Path.of(args[1]),disassemble(im));
            System.out.printf("Disassembled %d text bytes and %d data bytes to %s%n",im.text.length,im.data.length,args[1]);
        } catch(Exception e){System.err.println("Disassembler error: "+e.getMessage());System.exit(2);}
    }

    static boolean isAsciiBits(byte[] b){for(byte x:b){char c=(char)x;if(c!='0'&&c!='1'&&!Character.isWhitespace(c))return false;}return true;}
    static byte[] decodeAscii(byte[] b)throws Exception{String s=new String(b).replaceAll("\\s+","");if(s.length()%8!=0)throw new Exception("ASCII bit count is not a multiple of 8");ByteArrayOutputStream o=new ByteArrayOutputStream();for(int i=0;i<s.length();i+=8)o.write(Integer.parseInt(s.substring(i,i+8),2));return o.toByteArray();}
    static Image parse(byte[] b)throws Exception{if(b.length<24)throw new Exception("file is too short");int magic=word(b,0);if(magic!=MAGIC)throw new Exception("bad MHC magic; expected MHC1");int tb=word(b,4),tl=word(b,8),db=word(b,12),dl=word(b,16);if(tl<0||dl<0||24L+tl+dl!=b.length)throw new Exception("invalid section lengths in header");return new Image(tb,Arrays.copyOfRange(b,24,24+tl),db,Arrays.copyOfRange(b,24+tl,b.length));}
    static String disassemble(Image im)throws Exception{
        if(im.text.length%4!=0)throw new Exception("text section length is not a multiple of four");
        Set<Integer> targets=new TreeSet<>();
        for(int off=0;off<im.text.length;off+=4){int pc=im.textBase+off,w=word(im.text,off),op=w>>>26;if(op==2)targets.add(((pc+4)&0xF0000000)|((w&0x03ffffff)<<2));else if(op==4||op==5){int imm=(short)(w&0xffff);targets.add(pc+4+(imm<<2));}}
        StringBuilder s=new StringBuilder();
        s.append(".data\n");
        if(im.data.length>0){s.append("data_start:\n    .byte ");for(int i=0;i<im.data.length;i++){if(i>0)s.append(", ");s.append(im.data[i]&255);}s.append("\n");}
        s.append("\n.text\n.globl main\n\n");
        for(int off=0;off<im.text.length;off+=4){int pc=im.textBase+off;if(pc==im.textBase)s.append("main:\n");if(targets.contains(pc)&&pc!=im.textBase)s.append(label(pc)+":\n");s.append("    ").append(decode(word(im.text,off),pc)).append("\n");}
        return s.toString();
    }
    static String decode(int w,int pc)throws Exception{
        int op=w>>>26,rs=(w>>>21)&31,rt=(w>>>16)&31,rd=(w>>>11)&31,fun=w&63,imm=w&0xffff;short simm=(short)imm;
        if(w==0)return "nop";
        if(op==0){return switch(fun){case 0x0C->"syscall";case 0x20->"add "+R[rd]+", "+R[rs]+", "+R[rt];case 0x21->"addu "+R[rd]+", "+R[rs]+", "+R[rt];case 0x22->"sub "+R[rd]+", "+R[rs]+", "+R[rt];case 0x2A->"slt "+R[rd]+", "+R[rs]+", "+R[rt];case 0x1A->"div "+R[rs]+", "+R[rt];case 0x10->"mfhi "+R[rd];default->throw new Exception(String.format("unsupported R-format funct 0x%02X at 0x%08X",fun,pc));};}
        return switch(op){case 0x08->"addi "+R[rt]+", "+R[rs]+", "+simm;case 0x09->"addiu "+R[rt]+", "+R[rs]+", "+simm;case 0x0D->"ori "+R[rt]+", "+R[rs]+", "+imm;case 0x0F->"lui "+R[rt]+", "+imm;case 0x04->"beq "+R[rs]+", "+R[rt]+", "+label(pc+4+(simm<<2));case 0x05->"bne "+R[rs]+", "+R[rt]+", "+label(pc+4+(simm<<2));case 0x02->"j "+label(((pc+4)&0xF0000000)|((w&0x03ffffff)<<2));default->throw new Exception(String.format("unsupported opcode 0x%02X at 0x%08X",op,pc));};
    }
    static String label(int a){return String.format("L_%08X",a);}
    static int word(byte[] b,int i){return ((b[i]&255)<<24)|((b[i+1]&255)<<16)|((b[i+2]&255)<<8)|(b[i+3]&255);}
}
