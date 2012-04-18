//Variables
bool e=0;
bool e1=0;
bool e2=0;
bool e3=0;
bool e4=0;
bool e5=0;

active proctype source(){
   goto l1;

   // white states
   l1: atomic{goto f12;}
   l2: atomic{goto l3;}
   l3:  goto stop;

   // black states
   atomic{skip;
   }


   // inlining f1
   // white states

   // black states
   atomic{skip;
   f11: goto f12;
   f12: e1=0;goto f13;
   f13: goto l3;
   f14:  goto stop
   }


   stop: skip
}

active proctype p1(){
   goto l1;

   // white states

   // black states
   atomic{skip;
   l1: e1=1;goto l2;
   l2: e1==0 -> goto l3;
   l3: goto f22;
   l4: goto l5;
   l5:  goto stop
   }


   // inlining f2
   // white states

   // black states
   atomic{skip;
   f21: goto f22;
   f22: e2=0;goto f23;
   f23: goto l5;
   f24:  goto stop
   }


   stop: skip
}

active proctype p2(){
   goto l1;

   // white states

   // black states
   atomic{skip;
   l1: e2=1;goto l2;
   l2: e2==0 -> goto l3;
   l3: goto f32;
   l4: goto l5;
   l5:  goto stop
   }


   // inlining f3
   // white states

   // black states
   atomic{skip;
   f31: goto f32;
   f32: e3=0;goto f33;
   f33: goto l5;
   f34:  goto stop
   }


   stop: skip
}

active proctype p3(){
   goto l1;

   // white states

   // black states
   atomic{skip;
   l1: e3=1;goto l2;
   l2: e3==0 -> goto l3;
   l3: goto f42;
   l4: goto l5;
   l5:  goto stop
   }


   // inlining f4
   // white states

   // black states
   atomic{skip;
   f41: goto f42;
   f42: e4=0;goto f43;
   f43: goto l5;
   f44:  goto stop
   }


   stop: skip
}

active proctype p4(){
   goto l1;

   // white states

   // black states
   atomic{skip;
   l1: e4=1;goto l2;
   l2: e4==0 -> goto l3;
   l3: goto f52;
   l4: goto l5;
   l5:  goto stop
   }


   // inlining f5
   // white states

   // black states
   atomic{skip;
   f51: goto f52;
   f52: e5=0;goto f53;
   f53: goto l5;
   f54:  goto stop
   }


   stop: skip
}

active proctype p5(){
   goto l1;

   // white states

   // black states
   atomic{skip;
   l1: e5=1;goto l2;
   l2: e5==0 -> goto l3;
   l3: goto fsink2;
   l4: goto l5;
   l5:  goto stop
   }


   // inlining fsink
   // white states
   fsink1: atomic{goto fsink2;}
   fsink4:  goto stop;

   // black states
   atomic{skip;
   fsink2: e=0;goto fsink3;
   fsink3: goto l5;
   }


   stop: skip
}

active proctype sink(){
   goto l1;

   // white states
   l1: atomic{e=1;goto l2;}
   l2: atomic{e==0 -> goto l3;}
   l3: atomic{printf("MSC: assert(0)\n");goto l4;}
   l4:  goto stop;

   // black states
   atomic{skip;
   }



   stop: skip
}

