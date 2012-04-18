proctype PIni
	state   8 -(tr  51)-> state   2  [id   0 tp   3] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:67 => (((self==A)&&(party==B)))
	state   8 -(tr   2)-> state   4  [id   2 tp   3] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:67 => else
	state   2 -(tr  52)-> state   6  [id   1 tp   3] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:67 => IniRunningAB = 1
	state   6 -(tr   1)-> state   7  [id   5 tp   3] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:68 => .(goto)
	state   7 -(tr  53)-> state  17  [id   6 tp   3] [----G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:68 => ca!self,nonce,self,party
	state  17 -(tr  54)-> state  14  [id   8 tp 505,4] [A-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:73 => cc?eval(self),eval(nonce),g1,eval(party),eval(self)
	state  14 -(tr  55)-> state  11  [id   9 tp 505,4] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:74 => (((self==A)&&(party==B)))
	state  14 -(tr   2)-> state  13  [id  11 tp 505,4] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:74 => else
	state  11 -(tr  56)-> state  15  [id  10 tp 505,4] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:74 => IniCommitAB = 1
	state  15 -(tr   1)-> state  16  [id  14 tp 505,4] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:76 => .(goto)
	state  16 -(tr  57)-> state  18  [id  15 tp 505,4] [----G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:76 => cb!self,g1,party
	state  18 -(tr  58)-> state   0  [id  17 tp 3500] [--e-L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:78 => -end-
	state  13 -(tr   1)-> state  15  [id  12 tp 505,4] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:74 => (1)
	state   4 -(tr   1)-> state   6  [id   3 tp   3] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:67 => (1)
proctype PRes
	state   9 -(tr  43)-> state   6  [id  18 tp 503,5] [A-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:87 => ca?eval(self),g2,g3,eval(self)
	state   6 -(tr  44)-> state   3  [id  19 tp 503,5] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:88 => (((g3==A)&&(self==B)))
	state   6 -(tr   2)-> state   5  [id  21 tp 503,5] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:88 => else
	state   3 -(tr  45)-> state   7  [id  20 tp 503,5] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:88 => ResRunningAB = 1
	state   7 -(tr   1)-> state   8  [id  24 tp 503,5] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:90 => .(goto)
	state   8 -(tr  46)-> state  17  [id  25 tp 503,5] [----G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:90 => cc!self,g2,nonce,self,g3
	state  17 -(tr  47)-> state  15  [id  27 tp 504] [A-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:94 => cb?eval(self),eval(nonce),eval(self)
	state  15 -(tr  48)-> state  12  [id  28 tp 504] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:95 => (((g3==A)&&(self==B)))
	state  15 -(tr   2)-> state  14  [id  30 tp 504] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:95 => else
	state  12 -(tr  49)-> state  16  [id  29 tp 504] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:95 => ResCommitAB = 1
	state  16 -(tr   1)-> state  18  [id  33 tp 504] [----G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:96 => .(goto)
	state  18 -(tr  50)-> state   0  [id  35 tp 3500] [--e-L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:97 => -end-
	state  14 -(tr   1)-> state  16  [id  31 tp 504] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:95 => (1)
	state   5 -(tr   1)-> state   7  [id  22 tp 503,5] [A---G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:88 => (1)
proctype PI
	state 125 -(tr   8)-> state 125  [id  36 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,Na,B,A
	state 125 -(tr   9)-> state 125  [id  37 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (((kNa&&kNb)||k_Na_Nb_B__A)) -> (A) : (R) ),Na,Nb,B,A
	state 125 -(tr  10)-> state 125  [id  38 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,Ng,B,A
	state 125 -(tr  11)-> state 125  [id  39 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,A,B,A
	state 125 -(tr  12)-> state 125  [id  40 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,B,B,A
	state 125 -(tr  13)-> state 125  [id  41 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,I,B,A
	state 125 -(tr  14)-> state 125  [id  42 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,Na,I,A
	state 125 -(tr  15)-> state 125  [id  43 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( ((kNa&&kNb)) -> (A) : (R) ),Na,Nb,I,A
	state 125 -(tr  16)-> state 125  [id  44 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,Ng,I,A
	state 125 -(tr  17)-> state 125  [id  45 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,A,I,A
	state 125 -(tr  18)-> state 125  [id  46 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,B,I,A
	state 125 -(tr  19)-> state 125  [id  47 tp   5] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cc!( (kNa) -> (A) : (R) ),Na,I,I,A
	state 125 -(tr  20)-> state 125  [id  48 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( ((kNa||k_Na_A__B)) -> (B) : (R) ),Na,A,B
	state 125 -(tr  21)-> state 125  [id  49 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( (kNa) -> (B) : (R) ),Na,B,B
	state 125 -(tr  22)-> state 125  [id  50 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( (kNa) -> (B) : (R) ),Na,I,B
	state 125 -(tr  23)-> state 125  [id  51 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( (kNb) -> (B) : (R) ),Nb,A,B
	state 125 -(tr  24)-> state 125  [id  52 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( (kNb) -> (B) : (R) ),Nb,B,B
	state 125 -(tr  25)-> state 125  [id  53 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!( (kNb) -> (B) : (R) ),Nb,I,B
	state 125 -(tr  26)-> state 125  [id  54 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,Ng,A,B
	state 125 -(tr  27)-> state 125  [id  55 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,Ng,B,B
	state 125 -(tr  28)-> state 125  [id  56 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,Ng,I,B
	state 125 -(tr  29)-> state 125  [id  57 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,A,A,B
	state 125 -(tr  30)-> state 125  [id  58 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,A,B,B
	state 125 -(tr  31)-> state 125  [id  59 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,A,I,B
	state 125 -(tr  32)-> state 125  [id  60 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,B,A,B
	state 125 -(tr  33)-> state 125  [id  61 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,B,B,B
	state 125 -(tr  34)-> state 125  [id  62 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,B,I,B
	state 125 -(tr  35)-> state 125  [id  63 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,I,A,B
	state 125 -(tr  36)-> state 125  [id  64 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,I,B,B
	state 125 -(tr  37)-> state 125  [id  65 tp   3] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => ca!B,I,I,B
	state 125 -(tr  38)-> state 125  [id  66 tp   4] [--e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => cb!( ((kNb||k_Nb__B)) -> (B) : (R) ),Nb,B
	state 125 -(tr  39)-> state 125  [id  97 tp 503] [D-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => D_STEP
	state 125 -(tr  40)-> state 125  [id 119 tp 504] [D-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => D_STEP
	state 125 -(tr  41)-> state 125  [id 159 tp 505] [D-e-G] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:115 => D_STEP
init
	state   7 -(tr   3)-> state   5  [id 164 tp   2] [A---L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:191 => (run PIni(A,I,Na))
	state   7 -(tr   4)-> state   5  [id 165 tp   2] [A---L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:191 => (run PIni(A,B,Na))
	state   5 -(tr   5)-> state   6  [id 168 tp   2] [A---L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:195 => (run PRes(B,Nb))
	state   6 -(tr   6)-> state   8  [id 169 tp   2] [----L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:197 => (run PI())
	state   8 -(tr   7)-> state   0  [id 171 tp 3500] [--e-L] /Users/laarman/tests/pm/needham/model_1init_fixed.spin:199 => -end-

Transition Type: A=atomic; D=d_step; L=local; G=global
Source-State Labels: p=progress; e=end; a=accept;

pan: elapsed time 1.33e+09 seconds
pan: rate         0 states/second
