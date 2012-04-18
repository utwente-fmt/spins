#define myturn (M==0 || M==this)
int M=0;

//Variables
bool e=0;
bool e1=0;
bool e10=0;
bool e11=0;
bool e12=0;
bool e13=0;
bool e14=0;
bool e15=0;
bool e2=0;
bool e3=0;
bool e4=0;
bool e5=0;
bool e6=0;
bool e7=0;
bool e8=0;
bool e9=0;

//Functions
bool f1=0;
bool f10=0;
bool f11=0;
bool f12=0;
bool f13=0;
bool f14=0;
bool f15=0;
bool f2=0;
bool f3=0;
bool f4=0;
bool f5=0;
bool f6=0;
bool f7=0;
bool f8=0;
bool f9=0;
bool fsink=0;

active proctype source(){
   byte state=1;
   byte this =1;

   do
   :: atomic{myturn && state==1 -> f1=1;state=2;M=17}
   :: atomic{myturn && state==2 && f1==0 -> state=3;M=0}
   :: atomic{myturn && state==3 -> break}
   od
}


active proctype p1(){
   byte state=1;
   byte this =2;

   do
   :: atomic{myturn && state==1 -> e1=1;state=2;M=this}
   :: atomic{myturn && state==2 && e1==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f2=1;state=4;M=24}
   :: atomic{myturn && state==4 && f2==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p10(){
   byte state=1;
   byte this =3;

   do
   :: atomic{myturn && state==1 -> e10=1;state=2;M=this}
   :: atomic{myturn && state==2 && e10==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f11=1;state=4;M=19}
   :: atomic{myturn && state==4 && f11==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p11(){
   byte state=1;
   byte this =4;

   do
   :: atomic{myturn && state==1 -> e11=1;state=2;M=this}
   :: atomic{myturn && state==2 && e11==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f12=1;state=4;M=20}
   :: atomic{myturn && state==4 && f12==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p12(){
   byte state=1;
   byte this =5;

   do
   :: atomic{myturn && state==1 -> e12=1;state=2;M=this}
   :: atomic{myturn && state==2 && e12==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f13=1;state=4;M=21}
   :: atomic{myturn && state==4 && f13==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p13(){
   byte state=1;
   byte this =6;

   do
   :: atomic{myturn && state==1 -> e13=1;state=2;M=this}
   :: atomic{myturn && state==2 && e13==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f14=1;state=4;M=22}
   :: atomic{myturn && state==4 && f14==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p14(){
   byte state=1;
   byte this =7;

   do
   :: atomic{myturn && state==1 -> e14=1;state=2;M=this}
   :: atomic{myturn && state==2 && e14==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f15=1;state=4;M=23}
   :: atomic{myturn && state==4 && f15==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p15(){
   byte state=1;
   byte this =8;

   do
   :: atomic{myturn && state==1 -> e15=1;state=2;M=this}
   :: atomic{myturn && state==2 && e15==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> fsink=1;state=4;M=32}
   :: atomic{myturn && state==4 && fsink==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p2(){
   byte state=1;
   byte this =9;

   do
   :: atomic{myturn && state==1 -> e2=1;state=2;M=this}
   :: atomic{myturn && state==2 && e2==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f3=1;state=4;M=25}
   :: atomic{myturn && state==4 && f3==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p3(){
   byte state=1;
   byte this =10;

   do
   :: atomic{myturn && state==1 -> e3=1;state=2;M=this}
   :: atomic{myturn && state==2 && e3==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f4=1;state=4;M=26}
   :: atomic{myturn && state==4 && f4==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p4(){
   byte state=1;
   byte this =11;

   do
   :: atomic{myturn && state==1 -> e4=1;state=2;M=this}
   :: atomic{myturn && state==2 && e4==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f5=1;state=4;M=27}
   :: atomic{myturn && state==4 && f5==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p5(){
   byte state=1;
   byte this =12;

   do
   :: atomic{myturn && state==1 -> e5=1;state=2;M=this}
   :: atomic{myturn && state==2 && e5==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f6=1;state=4;M=28}
   :: atomic{myturn && state==4 && f6==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p6(){
   byte state=1;
   byte this =13;

   do
   :: atomic{myturn && state==1 -> e6=1;state=2;M=this}
   :: atomic{myturn && state==2 && e6==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f7=1;state=4;M=29}
   :: atomic{myturn && state==4 && f7==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p7(){
   byte state=1;
   byte this =14;

   do
   :: atomic{myturn && state==1 -> e7=1;state=2;M=this}
   :: atomic{myturn && state==2 && e7==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f8=1;state=4;M=30}
   :: atomic{myturn && state==4 && f8==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p8(){
   byte state=1;
   byte this =15;

   do
   :: atomic{myturn && state==1 -> e8=1;state=2;M=this}
   :: atomic{myturn && state==2 && e8==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f9=1;state=4;M=31}
   :: atomic{myturn && state==4 && f9==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p9(){
   byte state=1;
   byte this =16;

   do
   :: atomic{myturn && state==1 -> e9=1;state=2;M=this}
   :: atomic{myturn && state==2 && e9==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f10=1;state=4;M=18}
   :: atomic{myturn && state==4 && f10==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype fun_1(){
   byte state=1;
   byte this =17;

   do
   :: atomic{myturn && state==1 && f1==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e1=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f1=0;state=4;M=1}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_10(){
   byte state=1;
   byte this =18;

   do
   :: atomic{myturn && state==1 && f10==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e10=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f10=0;state=4;M=16}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_11(){
   byte state=1;
   byte this =19;

   do
   :: atomic{myturn && state==1 && f11==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e11=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f11=0;state=4;M=3}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_12(){
   byte state=1;
   byte this =20;

   do
   :: atomic{myturn && state==1 && f12==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e12=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f12=0;state=4;M=4}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_13(){
   byte state=1;
   byte this =21;

   do
   :: atomic{myturn && state==1 && f13==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e13=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f13=0;state=4;M=5}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_14(){
   byte state=1;
   byte this =22;

   do
   :: atomic{myturn && state==1 && f14==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e14=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f14=0;state=4;M=6}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_15(){
   byte state=1;
   byte this =23;

   do
   :: atomic{myturn && state==1 && f15==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e15=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f15=0;state=4;M=7}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_2(){
   byte state=1;
   byte this =24;

   do
   :: atomic{myturn && state==1 && f2==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e2=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f2=0;state=4;M=2}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_3(){
   byte state=1;
   byte this =25;

   do
   :: atomic{myturn && state==1 && f3==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e3=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f3=0;state=4;M=9}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_4(){
   byte state=1;
   byte this =26;

   do
   :: atomic{myturn && state==1 && f4==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e4=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f4=0;state=4;M=10}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_5(){
   byte state=1;
   byte this =27;

   do
   :: atomic{myturn && state==1 && f5==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e5=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f5=0;state=4;M=11}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_6(){
   byte state=1;
   byte this =28;

   do
   :: atomic{myturn && state==1 && f6==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e6=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f6=0;state=4;M=12}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_7(){
   byte state=1;
   byte this =29;

   do
   :: atomic{myturn && state==1 && f7==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e7=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f7=0;state=4;M=13}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_8(){
   byte state=1;
   byte this =30;

   do
   :: atomic{myturn && state==1 && f8==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e8=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f8=0;state=4;M=14}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_9(){
   byte state=1;
   byte this =31;

   do
   :: atomic{myturn && state==1 && f9==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e9=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f9=0;state=4;M=15}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_sink(){
   byte state=1;
   byte this =32;

   do
   :: atomic{myturn && state==1 && fsink==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e=0;state=3;M=this}
   :: atomic{myturn && state==3 -> fsink=0;state=4;M=8}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype sink(){
   byte state=1;
   byte this =33;

   do
   :: atomic{myturn && state==1 -> e=1;state=2;M=0}
   :: atomic{myturn && state==2 && e==0 -> state=3;M=0}
   :: atomic{myturn && state==3 -> printf("MSC: assert(0)\n");state=4;M=0}
   :: atomic{myturn && state==4 -> break}
   od
}


