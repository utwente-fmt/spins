--  Taylor's I-Protocol, as implemented in the GNU UUCP package
--  - restructed sender and receiver 
--  - removed source and sink processes (sndr_u and recv_u)
--  - removed hck and dck from packet, replaced with nondeteministic choice

CONST
    WindowSize	: 2;
    cancorrupt	: 1;
    fixed	: true;

    MaxSequence	: 2*WindowSize - 1;

TYPE
    Checksum		: (1-cancorrupt)..1;
    SequenceNumber	: 0..MaxSequence;
    PacketType		: ENUM {
				DATA,
				ACK,
				NAK
			  };
    Packet		: RECORD
				pty	: PacketType;
				seq	: SequenceNumber;
				ack	: SequenceNumber;
			  END;
    Channel		: RECORD
				full	: boolean;
				body	: Packet;
			  END;
    ChanID		: 0..1;
    SenderState		: ENUM {
				S_START,
				S_TIMEOUT_1,
				S_GETPKT_1
			  };
    ReceiverState	: ENUM {
				R_START,
				R_GETPKT_1,
				R_GETPKT_2,
				R_GETPKT_3,
				R_GETPKT_31,
				R_GETPKT_4
			  };
    Action		: ENUM {
				tau,
				progress
			  };

VAR
    -- Channels
    Channels	: array [ChanID] of Channel;

    -- Sender states
    SState	: SenderState;
    SSendSeq	: SequenceNumber;
    SRack	: SequenceNumber;
    Sseq	: SequenceNumber;

    -- Receiver states
    RState	: ReceiverState;
    RRecSeq	: SequenceNumber;
    RLack	: SequenceNumber;
    RNakd	: array[SequenceNumber] of boolean;
    RRecBuf	: array[SequenceNumber] of boolean;

    Rseq	: SequenceNumber;
    Rtmp	: SequenceNumber;

    -- test variables
    action	: Action;
    candrop	: boolean;
----------------------------------------------------------------------
----------------  Arithmetic Functions  ------------------------------
----------------------------------------------------------------------

FUNCTION inc(s: SequenceNumber) : SequenceNumber;
BEGIN
    IF s < MaxSequence THEN
	RETURN s+1;
    ELSE
	RETURN 0;
    END;
END;

FUNCTION sub(s1, s2: SequenceNumber) : SequenceNumber;
BEGIN
    IF s1 >= s2 THEN
	RETURN s1-s2;
    ELSE
	RETURN MaxSequence - s2 + 1 + s1;
    END;
END;

FUNCTION in_open_interval(s, l, r: SequenceNumber) : boolean;
BEGIN
    RETURN s != l & sub(l,s) <= WindowSize & sub(s,r) <= WindowSize;
END;

----------------------------------------------------------------------
----------  Formula monitor  -----------------------------------------
----------------------------------------------------------------------

PROCEDURE observe(a: Action);
BEGIN
    action := a;
END;

----------------------------------------------------------------------
----------------  Communication Functions  ---------------------------
----------------------------------------------------------------------

FUNCTION isfull(Chan: Channel) : boolean;
BEGIN
    return Chan.full;
END;

FUNCTION isempty(Chan: Channel) : boolean;
BEGIN
    return ! Chan.full;
END;

PROCEDURE input(VAR Chan: Channel; 
	VAR Pak: Packet);
BEGIN
    assert(isfull(Chan));
    Pak := Chan.body;
    CLEAR Chan;
END;

PROCEDURE output(VAR Chan: Channel; Pty: PacketType; Seq, Ack: SequenceNumber);
BEGIN
    assert(isempty(Chan));
    Chan.body.pty := Pty;
    Chan.body.seq := Seq;
    Chan.body.ack := Ack;
    Chan.full := true;
END;

----------------------------------------------------------------------
----------------  Processes Data and Ack Channels  -------------------
----------------------------------------------------------------------

RULESET n:ChanID DO
  ALIAS Chan: Channels[n] DO

    RULE "disable drop"
	candrop
    ==>
    BEGIN
	candrop := false;
    END;

    RULE "channel drop message"
	isfull(Chan) & candrop
    ==>
    BEGIN
	Chan.full := false;
	CLEAR Chan;
	observe(progress);
    END;

  ENDALIAS;
ENDRULESET;

----------------------------------------------------------------------
ALIAS 
	DataChan:Channels[0];
	AckChan: Channels[1];
--	openwindow:((sub(SSendSeq, SRack) <= WindowSize) & (SSendSeq != SRack))
DO
----------------------------------------------------------------------

----------------------------------------------------------------------
----------------  Process Sender  ------------------------------------
----------------------------------------------------------------------

RULE "sender send message"
    SState = S_START 
	& ((sub(SSendSeq, SRack) <= WindowSize) & (SSendSeq != SRack)) -- openwindow
	& isempty(DataChan)
==>
BEGIN
    observe(tau);
    output(DataChan, DATA, SSendSeq, 0);
    SSendSeq := inc(SSendSeq);
END;

RULE "sender getpkt"
    SState = S_START
	& isfull(AckChan)
==>
VAR
    pak : Packet;
BEGIN
    input(AckChan, pak);
	ASSERT(pak.pty = ACK | pak.pty = NAK);
	IF in_open_interval(pak.ack, SSendSeq, SRack) THEN
	    SRack := pak.ack;
	ENDIF;
	IF pak.pty = NAK THEN
	    IF in_open_interval(pak.seq, SSendSeq, SRack) THEN
		Sseq := pak.seq;
		SState := S_GETPKT_1;
	    ENDIF;
	ENDIF;
	observe(tau);
END;

RULE "sender getpkt 1" -- output
    SState = S_GETPKT_1
	& isempty(DataChan)
==>
BEGIN
    output(DataChan, DATA, Sseq, 0);
    SState := S_START;
    CLEAR Sseq;
    observe(tau);
END;


RULE "sender timeout"
    SState = S_START
	& isempty(DataChan)
==>
BEGIN
    output(DataChan, NAK, 1, 0);
--    IF openwindow THEN
    IF ((sub(SSendSeq, SRack) <= WindowSize) & (SSendSeq != SRack)) THEN
	observe(progress);	-- arbitary timeout as progress
    ELSE
	observe(tau);
    END;
    IF SSendSeq != inc(SRack) THEN
	SState := S_TIMEOUT_1;
    ELSE
	SState := S_START;
    END;
END;

RULE "sender timeout 1"
    SState = S_TIMEOUT_1
	& isempty(DataChan)
==>
BEGIN
    output(DataChan, DATA, inc(SRack), 0);
    SState := S_START;
    observe(tau);
END;

----------------------------------------------------------------------
----------------  Process Receiver  ----------------------------------
----------------------------------------------------------------------

RULESET dck:Checksum DO
RULE "receiver getpkt" 
    RState = R_START
	& isfull(DataChan)
==>
VAR
    pak : Packet;
    tmp : SequenceNumber;
BEGIN
    -- set default action and state
    observe(tau);
    RState := R_START;

    input(DataChan, pak);

	SWITCH pak.pty
	CASE NAK:
	    -- handle_nak fix
	    IF fixed THEN
		RState := R_GETPKT_4;
	    ENDIF;
	CASE DATA:
	    -- handle_data
	    IF sub(pak.seq, RLack) <= WindowSize
		    & pak.seq != RLack THEN
		-- in window
		IF dck = 0 THEN
		    observe(progress);
/*		    IF pak.seq != RRecSeq
		       | RRecBuf[pak.seq]
		       | RNakd[pak.seq] THEN
*/		    IF pak.seq != RRecSeq
		       & !RRecBuf[pak.seq]
		       & sub(pak.seq, RRecSeq) <= WindowSize THEN
			RState := R_GETPKT_1;
			Rseq := pak.seq;
		    ENDIF;
		ELSE -- dck = true
		    RNakd[pak.seq] := false;
		    IF pak.seq != inc(RRecSeq) THEN
		        IF pak.seq != RRecSeq
			      & !RRecBuf[pak.seq] THEN
			    RRecBuf[pak.seq] := true;
			    tmp := inc(RRecSeq);
			    IF tmp != pak.seq THEN
				Rtmp := tmp;
				Rseq := pak.seq;
				RState := R_GETPKT_3;
			    ENDIF;
			ENDIF;
		    ELSE
			-- expected sequence number
			observe(progress);
			RRecSeq := pak.seq;
			tmp := inc(pak.seq);
			WHILE RRecBuf[tmp] DO
			    RRecSeq := tmp;
			    RRecBuf[tmp] := false;
			    tmp := inc(tmp);
			ENDWHILE;
			IF sub(pak.seq, RLack) >= WindowSize/2 THEN
			    RState := R_GETPKT_2;
			    Rseq := pak.seq;
			ENDIF;
		    ENDIF; -- pak.seq != inc(RRecSeq)
		ENDIF; -- dck = false
	    ENDIF; -- in window
	-- CASE ACK: skip
	ENDSWITCH; -- pak.pty
END;
ENDRULESET; -- dck:Checksum

RULE "receiver getpkt 1"
    RState = R_GETPKT_1 &
	isempty(AckChan)
==>
BEGIN
    output(AckChan, NAK, Rseq, RRecSeq);
    RLack := RRecSeq;
    RNakd[Rseq] := true;
    CLEAR Rseq;
    RState := R_START;
    observe(tau);
END;

RULE "receiver getpkt 2"
    RState = R_GETPKT_2 &
	isempty(AckChan)
==>
BEGIN
    output(AckChan, ACK, Rseq, Rseq);
    RLack := RRecSeq;
    CLEAR Rseq;
    RState := R_START;
    observe(tau);
END;

RULE "receiver getpkt 3"
    RState = R_GETPKT_3
==>
BEGIN
    IF !RNakd[Rtmp] & !RRecBuf[Rtmp] THEN
	RState := R_GETPKT_31;
    ELSE
	Rtmp := inc(Rtmp);
	WHILE Rtmp != Rseq & (RNakd[Rtmp] | RRecBuf[Rtmp]) DO
	    Rtmp := inc(Rtmp);
	ENDWHILE;
	IF Rtmp = Rseq THEN
	    CLEAR Rtmp;
	    CLEAR Rseq;
	    RState := R_START;
	ELSE
	    RState := R_GETPKT_31;
	ENDIF;
    ENDIF;
    observe(tau);
END;

RULE "receiver getpkt 31"
    RState = R_GETPKT_31 &
	isempty(AckChan)
==>
BEGIN
    output(AckChan, NAK, Rtmp, RRecSeq);
    RLack := RRecSeq;
    RNakd[Rtmp] := true;
    Rtmp := inc(Rtmp);
    IF Rtmp = Rseq THEN
	CLEAR Rtmp;
	CLEAR Rseq;
	RState := R_START;
    ELSE
	RState := R_GETPKT_3;
    ENDIF;
    observe(tau);
END;

RULE "receiver getpkt 4"
    RState = R_GETPKT_4
	& isempty(AckChan)
==>
BEGIN
    output(AckChan, ACK, RRecSeq, RRecSeq);
    RLack := RRecSeq;
    RState := R_START;
    observe(tau);
END;

RULE "receiver timeout"
    RState = R_START &
	isempty(AckChan)
==>
BEGIN
    output(AckChan, NAK, inc(RRecSeq), RRecSeq);
    observe(progress);
    CLEAR RNakd;
    RLack := RRecSeq;
    RNakd[inc(RRecSeq)] := true;
END;

----------------------------------------------------------------------
----------------  Liveness requirements  -----------------------------
----------------------------------------------------------------------

LIVENESS "no livelock"
	ALWAYS EVENTUALLY (action = progress);

--LIVENESS "livelock"
--	EVENTUALLY ALWAYS (action = tau);

----------------------------------------------------------------------
ENDALIAS;
----------------------------------------------------------------------

STARTSTATE
    -- channel states
    CLEAR Channels;

    -- sender states
    SState	:= S_START;
    SSendSeq	:= 1;
    SRack	:= 0;
    CLEAR Sseq;

    -- receiver states
    RState	:= R_START;
    RRecSeq	:= 0;
    RLack	:= 0;
    CLEAR RNakd;
    CLEAR RRecBuf;
    CLEAR Rseq;
    CLEAR Rtmp;

    -- test variables
    action	:= tau;
    candrop	:= true;
END;
