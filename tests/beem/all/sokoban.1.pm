byte a[64];
byte x=4;
byte y=6;
byte done=0;


init { 
 d_step { 
a[0] =1; a[1] =1; a[2] =1; a[3] =1; a[4] =1; a[5] =1; a[6] =1; a[7] =1; a[8] =1; a[9] =0; a[10] =0; a[11] =0; a[12] =1; a[13] =1; a[14] =1; a[15] =1; a[16] =1; a[17] =2; a[18] =1; a[19] =0; a[20] =1; a[21] =1; a[22] =1; a[23] =1; a[24] =1; a[25] =0; a[26] =0; a[27] =0; a[28] =0; a[29] =0; a[30] =0; a[31] =1; a[32] =1; a[33] =0; a[34] =0; a[35] =2; a[36] =0; a[37] =2; a[38] =0; a[39] =1; a[40] =1; a[41] =0; a[42] =2; a[43] =0; a[44] =1; a[45] =0; a[46] =1; a[47] =1; a[48] =1; a[49] =1; a[50] =1; a[51] =0; a[52] =0; a[53] =0; a[54] =1; a[55] =1; a[56] =1; a[57] =1; a[58] =1; a[59] =1; a[60] =1; a[61] =1; a[62] =1; a[63] =1; }
atomic { 
run P();
} }

proctype P() { 

q: if
::  d_step {done==0 && a[25]==2 && a[35]==2 && a[42]==2 && a[37]==2;done = 1;}  goto q; 

::  d_step {done==0 && a[((y)*8+x-1)]==0;x = x-1;}  goto q; 

::  d_step {done==0 && a[((y)*8+x+1)]==0;x = x+1;}  goto q; 

::  d_step {done==0 && a[((y-1)*8+x)]==0;y = y-1;}  goto q; 

::  d_step {done==0 && a[((y+1)*8+x)]==0;y = y+1;}  goto q; 

::  d_step {done==0 && a[((y)*8+x-1)]==2 && a[((y)*8+x-2)]==0;a[((y)*8+x-2)] = 2;a[((y)*8+x-1)] = 0;x = x-1;}  goto q; 

::  d_step {done==0 && a[((y)*8+x+1)]==2 && a[((y)*8+x+2)]==0;a[((y)*8+x+2)] = 2;a[((y)*8+x+1)] = 0;x = x+1;}  goto q; 

::  d_step {done==0 && a[((y-1)*8+x)]==2 && a[((y-2)*8+x)]==0;a[((y-2)*8+x)] = 2;a[((y-1)*8+x)] = 0;y = y-1;}  goto q; 

::  d_step {done==0 && a[((y+1)*8+x)]==2 && a[((y+2)*8+x)]==0;a[((y+2)*8+x)] = 2;a[((y+1)*8+x)] = 0;y = y+1;}  goto q; 

fi;
}
