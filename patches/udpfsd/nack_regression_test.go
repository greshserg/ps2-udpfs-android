package udprdma

import (
	"net"
	"reflect"
	"testing"
)

// Callbacks and assertions run under the session lock, including timer writes.
func androidTestSession(t *testing.T) (*Session, *[][]byte) {
	t.Helper()
	var packets [][]byte
	s := NewSession(net.UDPAddr{}, func(_ *net.UDPAddr, data []byte) {
		packets = append(packets, append([]byte(nil), data...))
	}, nil)
	t.Cleanup(s.Close)
	return s, &packets
}

func androidPacketSeqs(t *testing.T, packets [][]byte) []uint16 {
	t.Helper()
	seqs := make([]uint16, 0, len(packets))
	for _, packet := range packets {
		h, err := UnpackHeader(packet)
		if err != nil { t.Fatal(err) }
		seqs = append(seqs, h.SeqNr)
	}
	return seqs
}

func TestAndroidRetransmitWindow(t *testing.T) {
	for _, tc := range []struct {
		name string
		start, request uint16
		want []uint16
	}{
		{"first", 339, 339, []uint16{339,340,341,342,343,344,345,346}},
		{"middle", 339, 343, []uint16{343,344,345,346}},
		{"last", 339, 346, []uint16{346}},
		{"sequence_wrap", 4092, 0, []uint16{0,1,2,3}},
		{"stale", 339, 338, []uint16{}},
		{"not_sent", 339, 347, []uint16{}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, packets := androidTestSession(t)
			s.Lock()
			defer s.Unlock()
			s.txSeqNr = tc.start
			s.txSeqNrAcked = (tc.start - 1) & 0xFFF
			// Also cross the physical ring boundary independently of sequence wrap.
			s.txReadIndex, s.txWriteIndex = 2046, 2046
			for i := 0; i < SendWindow; i++ { s.packDataPacket(nil, []byte{byte(i)}, false) }
			count := s.retransmitFrom(tc.request)
			if got := androidPacketSeqs(t, *packets); !reflect.DeepEqual(got, tc.want) || count != len(tc.want) {
				t.Fatalf("request %d: got %v (%d packets), want %v", tc.request, got, count, tc.want)
			}
		})
	}
}

// Reproduce a 12-packet reply stalled after an 8-packet window. The peer
// either missed a middle packet, or received the window but lost its ACK.
func TestAndroidNackProgress(t *testing.T) {
	for _, tc := range []struct {
		name string
		start, request, ack uint16
		want []uint16
	}{
		{"middle", 339, 343, 342, []uint16{343,344,345,346,347,348,349,350}},
		{"lost_window_ack", 339, 347, 346, []uint16{347,348,349,350}},
		{"wrapped_lost_ack", 4092, 4, 3, []uint16{4,5,6,7}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, packets := androidTestSession(t)
			s.Lock()
			defer s.Unlock()
			s.txSeqNr, s.txSeqNrAcked = tc.start, (tc.start - 1) & 0xFFF
			s.transfer = transfer{data: make([]byte, 12*1024), maxChunk: 1024}
			s.handleTransfer()
			if len(*packets) != SendWindow { t.Fatal("expected an initial full window") }
			*packets = nil
			s.onPeerNack(tc.request)
			if got := androidPacketSeqs(t, *packets); !reflect.DeepEqual(got, tc.want) {
				t.Fatalf("NACK %d: sent %v, want %v", tc.request, got, tc.want)
			}
			if s.txSeqNrAcked != tc.ack { t.Fatalf("acked %d, want %d", s.txSeqNrAcked, tc.ack) }
			if s.InFlight() > SendWindow { t.Fatal("exceeded send window") }
			s.onPeerAck((tc.start + 11) & 0xFFF)
			if s.InFlight() != 0 || s.finPending || s.transfer.data != nil || s.ackWaitMode != ackWaitNone {
				t.Fatal("reply did not complete after final ACK")
			}
		})
	}
}

func TestAndroidInvalidNackPreservesWindow(t *testing.T) {
	for _, seq := range []uint16{338, 348} {
		s, packets := androidTestSession(t)
		s.Lock()
		s.txSeqNr, s.txSeqNrAcked = 339, 338
		s.transfer = transfer{data: make([]byte, 12*1024), maxChunk: 1024}
		s.handleTransfer()
		*packets = nil
		s.retransmitAttempts = 2
		s.onPeerNack(seq)
		valid := len(*packets) == 0 && s.txSeqNrAcked == 338 && s.InFlight() == SendWindow && s.retransmitAttempts == 2
		s.Unlock()
		if !valid { t.Fatalf("invalid NACK %d changed send state", seq) }
	}
}
