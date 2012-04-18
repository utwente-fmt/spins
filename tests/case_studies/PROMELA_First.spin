#define automatic 0 
#define not_ready 1 
#define ready 2 
#define manual 3 
#define free 4 
#define occupied 5 
#define normal 6 
#define undefined 7 
#define right 8 
#define left 9 
#define error 10 
#define warmup 11 
#define stop 12 
#define proceed 13 
#define idle 14 
#define active 15 
#define preparing 16 
#define running 17 
#define Initial_0 18 
#define Initial_TEMPORARY 19 
#define to_left 20 
#define to_right 21 
#define at_right 22 
#define reserve_route 23 
#define cancel_route 24 
#define set_proceed 25 
#define set_stop 26 
#define request_completed 27 
#define at_left 28 
#define move_right 29 
#define move_left 30 
#define go_left 31 
#define go_right 32 
#define element 33 
#define track 34 
#define point 35 
#define signal 36 
#define route 37 
#define application 38 
#define show_proceed 39 
#define show_stop 40 

int track_track[3];	
int track_element[3];	
int track_automatic_automatic[3];	
int signal_signal[1];	
int signal_element[1];	
int signal_automatic_automatic[1];	
int point_point[1];	
int point_normal_detected[1];	
int point_normal_requested[1];	
int point_element[1];	
int point_automatic_automatic[1];	
int route_route[2];	
int route_active_active[2];	

proctype T1(chan obj_chan) {
  atomic { 
    mtype msg_name;
    int id = 0;
    track_track[id] = Initial_0;
    track_element[id] = Initial_0;
    track_automatic_automatic[id] = Initial_0;
  }  
  
  do  
  ::atomic { (track_automatic_automatic[id] == Initial_0) && (track_element[id] == automatic);
      track_automatic_automatic[id] = not_ready;
      printf("MSC: state_not_ready\n");
      track_element[id] = automatic;
      printf("MSC: state_automatic\n");
  }


  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == track) && (track_track[id] == Initial_0);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == occupied) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!not_ready;
        track_track[id] = occupied;
        printf("MSC: state_occupied\n");
    ::(msg_name == free) && (track_track[id] == occupied);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == automatic) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!ready;
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == element) && (track_element[id] == Initial_0);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == manual) && (track_element[id] == automatic);
        track_element[id] = manual;
        printf("MSC: state_manual\n");
    ::(msg_name == automatic) && (track_element[id] == manual);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == ready) && (track_automatic_automatic[id] == not_ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = ready;
        printf("MSC: state_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == not_ready) && (track_automatic_automatic[id] == ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = not_ready;
        printf("MSC: state_not_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype T2(chan obj_chan) {
  atomic { 
    mtype msg_name;
    int id = 1;
    track_track[id] = Initial_0;
    track_element[id] = Initial_0;
    track_automatic_automatic[id] = Initial_0;
  }  
  
  do  
  ::atomic { (track_automatic_automatic[id] == Initial_0) && (track_element[id] == automatic);
      track_automatic_automatic[id] = not_ready;
      printf("MSC: state_not_ready\n");
      track_element[id] = automatic;
      printf("MSC: state_automatic\n");
  }


  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == track) && (track_track[id] == Initial_0);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == occupied) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!not_ready;
        track_track[id] = occupied;
        printf("MSC: state_occupied\n");
    ::(msg_name == free) && (track_track[id] == occupied);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == automatic) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!ready;
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == element) && (track_element[id] == Initial_0);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == manual) && (track_element[id] == automatic);
        track_element[id] = manual;
        printf("MSC: state_manual\n");
    ::(msg_name == automatic) && (track_element[id] == manual);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == ready) && (track_automatic_automatic[id] == not_ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = ready;
        printf("MSC: state_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == not_ready) && (track_automatic_automatic[id] == ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = not_ready;
        printf("MSC: state_not_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype T3(chan obj_chan) {
  atomic { 
    mtype msg_name;
    int id = 2;
    track_track[id] = Initial_0;
    track_element[id] = Initial_0;
    track_automatic_automatic[id] = Initial_0;
  }  
  
  do  
  ::atomic { (track_automatic_automatic[id] == Initial_0) && (track_element[id] == automatic);
      track_automatic_automatic[id] = not_ready;
      printf("MSC: state_not_ready\n");
      track_element[id] = automatic;
      printf("MSC: state_automatic\n");
  }


  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == track) && (track_track[id] == Initial_0);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == occupied) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!not_ready;
        track_track[id] = occupied;
        printf("MSC: state_occupied\n");
    ::(msg_name == free) && (track_track[id] == occupied);
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == automatic) && (track_track[id] == free);
        assert(nfull(obj_chan));
        obj_chan!ready;
        track_track[id] = free;
        printf("MSC: state_free\n");
    ::(msg_name == element) && (track_element[id] == Initial_0);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == manual) && (track_element[id] == automatic);
        track_element[id] = manual;
        printf("MSC: state_manual\n");
    ::(msg_name == automatic) && (track_element[id] == manual);
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == ready) && (track_automatic_automatic[id] == not_ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = ready;
        printf("MSC: state_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == not_ready) && (track_automatic_automatic[id] == ready) && (track_element[id] == automatic);
        track_automatic_automatic[id] = not_ready;
        printf("MSC: state_not_ready\n");
        track_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype S1(chan obj_chan) {
  atomic { 
    mtype msg_name;
    int id = 0;
    signal_signal[id] = Initial_0;
    signal_element[id] = Initial_0;
    signal_automatic_automatic[id] = Initial_0;
  }  
  
  do  
  ::atomic { (signal_automatic_automatic[id] == Initial_0) && (signal_element[id] == automatic);
      signal_automatic_automatic[id] = not_ready;
      printf("MSC: state_not_ready\n");
      signal_element[id] = automatic;
      printf("MSC: state_automatic\n");
  }

    ::atomic { (timeout) && (signal_signal[id] == warmup);
        assert(nfull(obj_chan));
        obj_chan!ready;
        signal_signal[id] = stop;
        printf("MSC: state_stop\n");
    }
  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == signal) && (signal_signal[id] == Initial_0);
        signal_signal[id] = warmup;
        printf("MSC: state_warmup\n");
    ::(msg_name == set_proceed) && (signal_signal[id] == stop);
        signal_signal[id] = proceed;
        printf("MSC: state_proceed\n");
    ::(msg_name == set_stop) && (signal_signal[id] == proceed);
        signal_signal[id] = stop;
        printf("MSC: state_stop\n");
    ::(msg_name == element) && (signal_element[id] == Initial_0);
        signal_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == manual) && (signal_element[id] == automatic);
        signal_element[id] = manual;
        printf("MSC: state_manual\n");
    ::(msg_name == automatic) && (signal_element[id] == manual);
        signal_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == ready) && (signal_automatic_automatic[id] == not_ready) && (signal_element[id] == automatic);
        signal_automatic_automatic[id] = ready;
        printf("MSC: state_ready\n");
        signal_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == not_ready) && (signal_automatic_automatic[id] == ready) && (signal_element[id] == automatic);
        signal_automatic_automatic[id] = not_ready;
        printf("MSC: state_not_ready\n");
        signal_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype P1(chan obj_chan) {
  atomic { 
    mtype msg_name;
    int id = 0;
    point_point[id] = Initial_0;
    point_normal_detected[id] = Initial_0;
    point_normal_requested[id] = Initial_0;
    point_element[id] = Initial_0;
    point_automatic_automatic[id] = Initial_0;
  }  
  
  do  
  ::atomic { (point_normal_detected[id] == Initial_0) && (point_point[id] == normal);
      point_normal_detected[id] = undefined;
      printf("MSC: state_undefined\n");
      point_point[id] = normal;
      printf("MSC: state_normal\n");
  }  ::atomic { (point_normal_requested[id] == Initial_0) && (point_point[id] == normal);
      point_normal_requested[id] = left;
      printf("MSC: state_left\n");
      point_point[id] = normal;
      printf("MSC: state_normal\n");
  }  ::atomic { (point_automatic_automatic[id] == Initial_0) && (point_element[id] == automatic);
      point_automatic_automatic[id] = not_ready;
      printf("MSC: state_not_ready\n");
      point_element[id] = automatic;
      printf("MSC: state_automatic\n");
  }

    ::atomic { (timeout) && (point_normal_detected[id] == undefined) && (point_point[id] == normal);
        point_point[id] = error;
        printf("MSC: state_error\n");
    }
  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == point) && (point_point[id] == Initial_0);
        assert(nfull(obj_chan));
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == go_right) && (point_point[id] == normal);
        assert(nfull(obj_chan));
        obj_chan!to_right;
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == go_left) && (point_point[id] == normal);
        assert(nfull(obj_chan));
        obj_chan!to_left;
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == element) && (point_element[id] == Initial_0);
        point_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == manual) && (point_element[id] == automatic);
        point_element[id] = manual;
        printf("MSC: state_manual\n");
    ::(msg_name == automatic) && (point_element[id] == manual);
        point_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == at_right) && (point_point[id] == normal);
        point_normal_detected[id] = right;
        printf("MSC: state_right\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == at_left) && (point_point[id] == normal);
        point_normal_detected[id] = left;
        printf("MSC: state_left\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == to_right) && (point_point[id] == normal);
        point_normal_detected[id] = undefined;
        printf("MSC: state_undefined\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == to_left) && (point_point[id] == normal);
        point_normal_detected[id] = undefined;
        printf("MSC: state_undefined\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == to_right) && (point_point[id] == normal);
        point_normal_requested[id] = right;
        printf("MSC: state_right\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::(msg_name == to_left) && (point_point[id] == normal);
        point_normal_requested[id] = left;
        printf("MSC: state_left\n");
        point_point[id] = normal;
        printf("MSC: state_normal\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == ready) && (point_automatic_automatic[id] == not_ready) && (point_element[id] == automatic);
        point_automatic_automatic[id] = ready;
        printf("MSC: state_ready\n");
        point_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::(msg_name == not_ready) && (point_automatic_automatic[id] == ready) && (point_element[id] == automatic);
        point_automatic_automatic[id] = not_ready;
        printf("MSC: state_not_ready\n");
        point_element[id] = automatic;
        printf("MSC: state_automatic\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype R1(chan obj_chan; chan point_P1; chan track_T1; chan track_T3; chan signal_S1) {
  atomic { 
    mtype msg_name;
    int id = 0;
    route_route[id] = Initial_0;
    route_active_active[id] = -1;
  }  
  
  do  

  ::atomic { (route_active_active[id] == preparing) && (route_route[id] == active);
      assert(nfull(signal_S1));
      signal_S1!set_proceed;
      route_active_active[id] = ready;
      printf("MSC: state_ready\n");
      route_route[id] = active;
      printf("MSC: state_active\n");
  }  ::atomic { (route_active_active[id] == ready) && (route_route[id] == active);
      route_route[id] = idle;
      printf("MSC: state_idle\n");
  }

  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == route) && (route_route[id] == Initial_0);
        route_route[id] = idle;
        printf("MSC: state_idle\n");
    ::(msg_name == cancel_route) && (route_route[id] == active);
        route_route[id] = idle;
        printf("MSC: state_idle\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == reserve_route) && (route_route[id] == idle);
        assert(nfull(point_P1));
        point_P1!to_left;
        route_active_active[id] = preparing;
        printf("MSC: state_preparing\n");
        route_route[id] = active;
        printf("MSC: state_active\n");
    ::else -> skip;
    fi;
  }
  od;   
}

proctype R2(chan obj_chan; chan point_P1; chan track_T1; chan track_T2; chan signal_S1) {
  atomic { 
    mtype msg_name;
    int id = 1;
    route_route[id] = Initial_0;
    route_active_active[id] = -1;
  }  
  
  do  

  ::atomic { (route_active_active[id] == preparing) && (route_route[id] == active);
      assert(nfull(signal_S1));
      signal_S1!set_proceed;
      route_active_active[id] = ready;
      printf("MSC: state_ready\n");
      route_route[id] = active;
      printf("MSC: state_active\n");
  }  ::atomic { (route_active_active[id] == ready) && (route_route[id] == active);
      route_route[id] = idle;
      printf("MSC: state_idle\n");
  }

  ::atomic { timeout && nempty(obj_chan);
    obj_chan?msg_name;
    if
    ::(msg_name == route) && (route_route[id] == Initial_0);
        route_route[id] = idle;
        printf("MSC: state_idle\n");
    ::(msg_name == cancel_route) && (route_route[id] == active);
        route_route[id] = idle;
        printf("MSC: state_idle\n");
    ::else -> skip;
    fi;
    if
    ::(msg_name == reserve_route) && (route_route[id] == idle);
        assert(nfull(point_P1));
        point_P1!to_right;
        route_active_active[id] = preparing;
        printf("MSC: state_preparing\n");
        route_route[id] = active;
        printf("MSC: state_active\n");
    ::else -> skip;
    fi;
  }
  od;   
}


init {
  atomic {
    chan T1_ = [5] of { mtype };
    chan T2_ = [5] of { mtype };
    chan T3_ = [5] of { mtype };
    chan S1_ = [5] of { mtype };
    chan P1_ = [5] of { mtype };
    chan R1_ = [5] of { mtype };
    chan R2_ = [5] of { mtype };

    run T1(T1_);
    run T2(T2_);
    run T3(T3_);
    run S1(S1_);
    run P1(P1_);
    run R1(R1_, P1_, T1_, T3_, S1_);
    run R2(R2_, P1_, T1_, T2_, S1_);
  }
  do
    ::atomic { timeout && empty(T1_) && empty(T2_) && empty(T3_) && empty(S1_) && empty(P1_) && empty(R1_) && empty(R2_);
      if
      ::(track_track[0] == free) -> T1_!occupied;
      ::(track_track[0] == occupied) -> T1_!free;
      ::(track_track[0] == free) -> T1_!automatic;
      ::(track_track[0] == Initial_0) -> T1_!track;
      ::(track_element[0] == automatic) -> T1_!manual;
      ::(track_element[0] == Initial_0) -> T1_!element;
      ::(track_element[0] == manual) -> T1_!automatic;
      ::(track_track[1] == free) -> T2_!occupied;
      ::(track_track[1] == occupied) -> T2_!free;
      ::(track_track[1] == free) -> T2_!automatic;
      ::(track_track[1] == Initial_0) -> T2_!track;
      ::(track_element[1] == automatic) -> T2_!manual;
      ::(track_element[1] == Initial_0) -> T2_!element;
      ::(track_element[1] == manual) -> T2_!automatic;
      ::(track_track[2] == free) -> T3_!occupied;
      ::(track_track[2] == occupied) -> T3_!free;
      ::(track_track[2] == free) -> T3_!automatic;
      ::(track_track[2] == Initial_0) -> T3_!track;
      ::(track_element[2] == automatic) -> T3_!manual;
      ::(track_element[2] == Initial_0) -> T3_!element;
      ::(track_element[2] == manual) -> T3_!automatic;
      ::(signal_signal[0] == Initial_0) -> S1_!signal;
      ::(signal_signal[0] == proceed) -> S1_!set_stop;
      ::(signal_element[0] == automatic) -> S1_!manual;
      ::(signal_element[0] == Initial_0) -> S1_!element;
      ::(signal_element[0] == manual) -> S1_!automatic;
      ::(point_point[0] == Initial_0) -> P1_!point;
      ::(point_point[0] == normal) -> P1_!at_left;
      ::(point_point[0] == normal) -> P1_!go_left;
      ::(point_point[0] == normal) -> P1_!go_right;
      ::(point_point[0] == normal) -> P1_!at_right;
      ::(point_element[0] == automatic) -> P1_!manual;
      ::(point_element[0] == Initial_0) -> P1_!element;
      ::(point_element[0] == manual) -> P1_!automatic;
      ::(route_route[0] == Initial_0) -> R1_!route;
      ::(route_route[0] == idle) -> R1_!reserve_route;
      ::(route_route[0] == active) -> R1_!cancel_route;
      ::(route_route[1] == Initial_0) -> R2_!route;
      ::(route_route[1] == idle) -> R2_!reserve_route;
      ::(route_route[1] == active) -> R2_!cancel_route;
      fi;
    }
  od;
}

