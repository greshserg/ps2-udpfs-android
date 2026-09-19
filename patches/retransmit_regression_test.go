package udprdma

import (
	"encoding/binary"
	"net"
	"slices"
	"testing"
	"time"
)

func TestAndroidRetransmitFrom(t *testing.T) {
	for _, tc := range []struct {
		name string
		seqs []uint16
		from uint16
		want []uint16
	}{
		{"log_reproduction", []uint16{2741, 2742, 2748, 2749, 2750}, 2749, []uint16{2749, 2750}},
		{"wrap_skip_old", []uint16{4094, 4095, 0, 1}, 0, []uint16{0, 1}},
		{"wrap_send", []uint16{4094, 4095, 0, 1}, 4095, []uint16{4095, 0, 1}},
		{"all_old", []uint16{2741, 2748}, 2749, nil},
		{"empty", nil, 0, nil},
		{"half_range", []uint16{2048, 4095, 0, 2047}, 0, []uint16{0, 2047}},
		{"half_range_wrapped", []uint16{2047, 4094, 4095, 2046}, 4095, []uint16{4095, 2046}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var got []uint16
			calls := 0
			s := &Session{}
			s.writeBatch = func(_ *net.UDPAddr, batch [][]byte) {
				calls++
				for _, p := range batch {
					got = append(got, binary.LittleEndian.Uint16(p))
				}
			}
			// Cross the physical ring boundary as well as the sequence boundary.
			s.txReadIndex = len(s.txBuffer) - 2
			s.txWriteIndex = s.txReadIndex
			for _, seq := range tc.seqs {
				data := make([]byte, 2)
				binary.LittleEndian.PutUint16(data, seq)
				s.txBuffer[s.txWriteIndex] = txPacket{seq: seq, data: data}
				s.txWriteIndex = (s.txWriteIndex + 1) % len(s.txBuffer)
			}
			read, write := s.txReadIndex, s.txWriteIndex
			if n := s.retransmitFrom(tc.from); n != len(tc.want) || !slices.Equal(got, tc.want) {
				t.Fatalf("sent %d packets %v; want %v", n, got, tc.want)
			}
			if int(s.packetsTx) != len(tc.want) || int(s.retransmits) != len(tc.want) {
				t.Fatal("incorrect retransmission counters")
			}
			if s.txReadIndex != read || s.txWriteIndex != write {
				t.Fatal("retransmission modified ring indices")
			}
			if (len(tc.want) == 0 && calls != 0) || (len(tc.want) > 0 && calls != 1) {
				t.Fatalf("unexpected batch calls: %d", calls)
			}
		})
	}
}

func TestAndroidWindowAckTimeout(t *testing.T) {
	if WindowAckTimeout != 250*time.Millisecond {
		t.Fatalf("WindowAckTimeout = %v", WindowAckTimeout)
	}
}
