mtype = {A1, A2, B, I, Na1, Na2, Nb, Ng, R};

#define k(x1)           if \
			:: (x1 == Na1)-> kNa1=1         \
			:: (x1 == Na2)-> kNa2=1         \
                        :: (x1 == Nb)-> kNb=1		\
			:: else skip \
                        fi; 

#define k2(x1,x2)	if \
			:: (x1 == Nb && x2==B)-> k_Nb__B=1        \
                        :: else skip   \
                        fi

#define k3(x1,x2,x3)    if \
			:: (x1 == Na1 && x2 == A1  && x3 == B)-> k_Na1_A1__B=1 \
			:: (x1 == Na2 && x2 == A2  && x3 == B)-> k_Na2_A2__B=1 \
                        :: (x1 == Na1 && x2 == Nb && x3 == A1)-> k_Na1_Nb__A1=1 \
			:: (x1 == Na2 && x2 == Nb && x3 == A1)-> k_Na2_Nb__A1=1 \
			:: (x1 == Na1 && x2 == Nb && x3 == A2)-> k_Na1_Nb__A2=1 \
			:: (x1 == Na2 && x2 == Nb && x3 == A2)-> k_Na2_Nb__A2=1 \
                        :: else skip \
			fi

#define IniRunning(x,y) if      \
			:: ((x==A1)&&(y==B))-> IniRunningA1B=1 \
			:: ((x==A2)&&(y==B))-> IniRunningA2B=1 \
			:: else skip \
			fi
#define IniCommit(x,y) if      \
			:: ((x==A1)&&(y==B))-> IniCommitA1B=1 \
			:: ((x==A2)&&(y==B))-> IniCommitA2B=1 \
			:: else skip \
			fi
#define ResRunning(x,y) if      \
			:: ((x==A1)&&(y==B))-> ResRunningA1B=1 \
			:: ((x==A2)&&(y==B))-> ResRunningA2B=1 \
			:: else skip \
			fi
#define ResCommit(x,y) if      \
			:: ((x==A1)&&(y==B))-> ResCommitA1B=1 \
			:: ((x==A2)&&(y==B))-> ResCommitA2B=1 \
			:: else skip \
			fi

bit IniRunningA1B=0;
bit IniCommitA1B=0;
bit ResRunningA1B=0;
bit ResCommitA1B=0;

bit IniRunningA2B=0;
bit IniCommitA2B=0;
bit ResRunningA2B=0;
bit ResCommitA2B=0;


hidden byte changes;

/********************************************
 *
 * Channels: 
 *       ca: type 1 messages {x1,x2}PK(x3) 
 *       cb: type 2 messages {x1}PK(x2)
 * 
 ********************************************/

chan ca = [0] of {mtype, mtype, mtype, mtype}; 
chan cb = [0] of {mtype, mtype, mtype};

/* Initiator */
proctype PIni (mtype self; mtype party; mtype nonce) 
{ 	
	mtype g1;
	
	atomic { 
        IniRunning(self,party);
        ca ! self, nonce, self, party; 
      }
	
end1:	
      atomic { 
        ca ? eval (self), eval (nonce), g1, eval (self);
	  IniCommit(self,party);

	  cb ! self, g1, party;
      }
}

/* Responder */
proctype PRes (mtype self; mtype nonce) 
{ 	
	mtype g2, g3; 

end2:
	atomic {	
	  ca ? eval (self), g2, g3, eval (self);
	  ResRunning(g3,self);

	  ca ! self, g2, nonce, g3;
	}
end3:
	atomic {
	  cb ? eval (self), eval (nonce), eval (self);
	  ResCommit(g3,self);
	}
}

proctype PI () 
{
	/* Intruder always knows 	A1, A2, B, I, Ng; */

	bit kNa1 = 0;        /* Intruder knows 	Na1 */
	bit kNa2 = 0;        /* Intruder knows 	Na1 */

	bit kNb = 0;        /* Intruder knows 	Nb */

	bit k_Na1_Nb__A1 = 0; /*     "       "     {Na1, Nb}{PK(A1)} */
	bit k_Na2_Nb__A1 = 0; /*     "       "     {Na2, Nb}{PK(A1)} */
	bit k_Na1_Nb__A2 = 0; /*     "       "     {Na1, Nb}{PK(A2)} */
	bit k_Na2_Nb__A2 = 0; /*     "       "     {Na2, Nb}{PK(A2)} */

	bit k_Na1_A1__B = 0;  /*     "       "     {Na1, A1}{PK(B)} */
	bit k_Na2_A2__B = 0;  /*     "       "     {Na2, A2}{PK(B)} */
	bit k_Nb__B = 0;    /*     "       "     {Nb}{PK(B)} */

	mtype x1=0,x2=0,x3=0;


end4:
	do

	::   ca ! ((kNa1 || k_Na1_A1__B) -> B : R), Na1, A1, B
	::   ca ! (kNa1 -> B : R), Na1, A2, B

	::   ca ! (kNa2 -> B : R), Na2, A1, B
	::   ca ! ((kNa2 || k_Na2_A2__B) -> B : R), Na2, A2, B

	::   ca ! (kNa1 -> B : R), Na1, B, B
	::   ca ! (kNa2 -> B : R), Na2, B, B
	::   ca ! (kNa1 -> B : R), Na1, I, B
	::   ca ! (kNa2 -> B : R), Na2, I, B
	::   ca ! (kNb -> B : R), Nb, A1, B
	::   ca ! (kNb -> B : R), Nb, A1, B
	::   ca ! (kNb -> B : R), Nb, B, B
	::   ca ! (kNb -> B : R), Nb, I, B

	::   ca ! B, Ng, A1, B
	::   ca ! B, Ng, A2, B
	::   ca ! B, Ng, B, B
	::   ca ! B, Ng, I, B
/*
	::   ca ! (kNa1 -> A1 : R), Na1, A1, A1
	::   ca ! (kNa2 -> A1 : R), Na2, A1, A1
	::   ca ! (kNa1 -> A1 : R), Na1, A2, A1
	::   ca ! (kNa2 -> A1 : R), Na2, A2, A1

	::   ca ! (kNa1 -> A2 : R), Na1, A1, A2
	::   ca ! (kNa2 -> A2 : R), Na2, A1, A2
	::   ca ! (kNa1 -> A2 : R), Na1, A2, A2
	::   ca ! (kNa2 -> A2 : R), Na2, A2, A2

	::   ca ! (kNa1 -> A1 : R), Na1, B, A1
	::   ca ! (kNa2 -> A1 : R), Na2, B, A1
	::   ca ! (kNa1 -> A2 : R), Na1, B, A2
	::   ca ! (kNa2 -> A2 : R), Na2, B, A2

	::   ca ! (kNa1 -> A1 : R), Na1, I, A1
	::   ca ! (kNa2 -> A1 : R), Na2, I, A1
	::   ca ! (kNa1 -> A2 : R), Na1, I, A2
	::   ca ! (kNa2 -> A2 : R), Na2, I, A2

	::   ca ! B, A1, A1, B
	::   ca ! B, A1, A2, B
	::   ca ! B, A2, A1, B
	::   ca ! B, A2, A2, B

	::   ca ! B, A1, B, B
	::   ca ! B, A2, B, B

	::   ca ! B, A1, I, B
	::   ca ! B, A2, I, B

	::   ca ! B, B, A1, B
	::   ca ! B, B, A2, B

	::   ca ! B, B, B, B
        ::   ca ! B, B, I, B

	::   ca ! B, I, A1, B
	::   ca ! B, I, A2, B

	::   ca ! B, I, B, B
        ::   ca ! B, I, I, B
*/
	::   ca ! (kNa1 -> A1 : R), Na1, Na1, A1
	::   ca ! ((kNa1 && kNa2) -> A1 : R), Na1, Na2, A1
	::   ca ! (kNa1 -> A2 : R), Na1, Na1, A2
	::   ca ! ((kNa1 && kNa2) -> A2 : R), Na1, Na2, A2
	::   ca ! ((kNa2 && kNa1) -> A1 : R), Na2, Na1, A1
	::   ca ! (kNa2 -> A1 : R), Na2, Na2, A1
	::   ca ! ((kNa2  && kNa1) -> A2 : R), Na2, Na1, A2
	::   ca ! (kNa2 -> A2 : R), Na2, Na2, A2

	::   ca ! (((kNa1 && kNb) || k_Na1_Nb__A1) -> A1 : R), Na1, Nb, A1
	::   ca ! (((kNa2 && kNb) || k_Na2_Nb__A1) -> A1 : R), Na2, Nb, A1
	::   ca ! (((kNa1 && kNb) || k_Na1_Nb__A2) -> A2 : R), Na1, Nb, A2
	::   ca ! (((kNa2 && kNb) || k_Na2_Nb__A2) -> A2 : R), Na2, Nb, A2

	::   ca ! (kNa1 -> A1 : R), Na1, Ng, A1
	::   ca ! (kNa2 -> A1 : R), Na2, Ng, A1
	::   ca ! (kNa1 -> A2 : R), Na1, Ng, A2
	::   ca ! (kNa2 -> A2 : R), Na2, Ng, A2

	::   cb ! ((k_Nb__B || kNb) -> B : R), Nb, B		

        :: d_step {
             ca ? _, x1, x2, x3; 	if
					:: (x3==I)-> k(x1); k(x2)
					:: else k3(x1,x2,x3)
					fi;
					x1 = 0;
					x2 = 0;
					x3 = 0; 
	     }

        :: d_step { 
		cb ? _, x1, x2; 	if
					:: (x2==I)-> k(x1)
					:: else k2(x1,x2)
					fi;
					x1 = 0;
					x2 = 0;
	     }
	od
}
#define __instances_PIni 2
#define __instances_PRes 1
#define __instances_PI 1

init
{
  atomic {
      if 
      :: run PIni (A1, I, Na1); run PIni (A2, I, Na2)
      :: run PIni (A1, B, Na1); run PIni (A2, I, Na2)
      :: run PIni (A1, I, Na1); run PIni (A2, B, Na2)
      fi;

      run PRes (B, Nb);

      run PI ();
  }
}

