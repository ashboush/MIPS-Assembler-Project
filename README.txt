MIPS FizzBuzz Assembler and Disassembler in Java

Files:
- Assembler.java: two-pass assembler for the subset used by FizzBuzz.asm.
- Disassembler.java: reads either .mhc-ascii or .mhc-bin and reconstructs equivalent MIPS.
- FizzBuzz.asm: original source.
- fizzbuzz.mhc-ascii: ASCII bit representation of the MHC container.
- fizzbuzz.mhc-bin: binary MHC container.
- fizzbuzz-disassembled.asm: reconstructed assembly.

Supported Source Features:
Sections/directives: .data, .text, .globl, .asciiz, .byte, labels.
Pseudoinstructions: li, move, rem with an immediate divisor, la, ble.
Real instructions: addi, addiu, add, addu, sub, slt, div, mfhi, beq, bne,
lui, ori, j, syscall, nop.

Pseudo-instruction Expansions:
li rt, imm        -> addiu rt, $zero, imm (16-bit signed values)
move rd, rs       -> addu rd, rs, $zero
rem rd, rs, imm   -> addiu $at,$zero,imm ; div rs,$at ; mfhi rd
la rt, label      -> lui rt,upper(address) ; ori rt,rt,lower(address)
ble rs, rt, label -> slt $at,rt,rs ; beq $at,$zero,label

Addresses / Byte Order:
.text begins at 0x00400000.
.data begins at 0x10010000.
32-bit words are stored big-endian.

mhc File Layout:
Both formats represent the same custom container so text and data remain distinguishable.
The first 24 bytes are six big-endian 32-bit words:
1. magic = 0x4D484331 (ASCII "MHC1")
2. text start address
3. text length in bytes
4. data start address
5. data length in bytes
6. reserved/flags = 0
Then come the text bytes, followed by the data bytes.

mhc-bin stores those bytes directly.
mhc-ascii writes every byte as eight readable 0/1 characters, with line breaks only
for readability. Thus a 32-bit instruction occupies 32 bit characters in ASCII but
four actual bytes in the binary payload.

Build and Run:
From the folder containing the files:
  javac Assembler.java Disassembler.java
  java Assembler FizzBuzz.asm fizzbuzz.mhc-ascii fizzbuzz.mhc-bin
  java Disassembler fizzbuzz.mhc-bin fizzbuzz-disassembled.asm

The disassembler also accepts ASCII input:
  java Disassembler fizzbuzz.mhc-ascii fizzbuzz-disassembled-from-ascii.asm

Round-Trip Verification:
  java Assembler fizzbuzz-disassembled.asm rebuilt.mhc-ascii rebuilt.mhc-bin
  cmp fizzbuzz.mhc-bin rebuilt.mhc-bin
No output from cmp means the files are byte-for-byte identical.

Error Handeling:
Unsupported instructions, unknown labels/registers, duplicate labels, malformed
strings, wrong operand counts, and out-of-range immediates produce a line-numbered
error and a nonzero exit status.

Limitations:
This is intentionally a subset assembler, not a complete MIPS assembler. rem currently
supports the immediate-divisor form used by this FizzBuzz. li supports signed 16-bit
constants. Data support is limited to .asciiz and .byte because those are sufficient
for the original and reconstructed program.

Acknowledgement
I used AI to help me code this. 
