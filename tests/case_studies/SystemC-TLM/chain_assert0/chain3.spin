#define myturn (M==0 || M==this)
int M=0;

//Variables
bool e=0;
bool e1=0;
bool e2=0;
bool e3=0;

//Functions
bool f1=0;
bool f2=0;
bool f3=0;
bool fsink=0;

active proctype source(){
   byte state=1;
   byte this =1;

   do
   :: atomic{myturn && state==1 -> f1=1;state=2;M=5}
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
   :: atomic{myturn && state==3 -> f2=1;state=4;M=6}
   :: atomic{myturn && state==4 && f2==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p2(){
   byte state=1;
   byte this =3;

   do
   :: atomic{myturn && state==1 -> e2=1;state=2;M=this}
   :: atomic{myturn && state==2 && e2==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> f3=1;state=4;M=7}
   :: atomic{myturn && state==4 && f3==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype p3(){
   byte state=1;
   byte this =4;

   do
   :: atomic{myturn && state==1 -> e3=1;state=2;M=this}
   :: atomic{myturn && state==2 && e3==0 -> state=3;M=this}
   :: atomic{myturn && state==3 -> fsink=1;state=4;M=8}
   :: atomic{myturn && state==4 && fsink==0 -> state=5;M=this}
   :: atomic{myturn && state==5 -> break}
   od
}


active proctype fun_1(){
   byte state=1;
   byte this =5;

   do
   :: atomic{myturn && state==1 && f1==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e1=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f1=0;state=4;M=1}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_2(){
   byte state=1;
   byte this =6;

   do
   :: atomic{myturn && state==1 && f2==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e2=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f2=0;state=4;M=2}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_3(){
   byte state=1;
   byte this =7;

   do
   :: atomic{myturn && state==1 && f3==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e3=0;state=3;M=this}
   :: atomic{myturn && state==3 -> f3=0;state=4;M=3}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype fun_sink(){
   byte state=1;
   byte this =8;

   do
   :: atomic{myturn && state==1 && fsink==1 -> state=2;M=this}
   :: atomic{myturn && state==2 -> e=0;state=3;M=this}
   :: atomic{myturn && state==3 -> fsink=0;state=4;M=4}
   :: atomic{myturn && state==4 -> break}
   od
}


active proctype sink(){
   byte state=1;
   byte this =9;

   do
   :: atomic{myturn && state==1 -> e=1;state=2;M=0}
   :: atomic{myturn && state==2 && e==0 -> state=3;M=0}
   :: atomic{myturn && state==3 -> printf("MSC: assert(0)\n");state=4;M=0}
   :: atomic{myturn && state==4 -> break}
   od
}


