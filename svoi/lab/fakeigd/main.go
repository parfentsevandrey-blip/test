// fakeigd is a stand-in for a home router's UPnP Internet Gateway Device, used
// by the real-NAT lab. Run inside the router's network namespace, it answers
// SSDP searches on the LAN side, speaks just enough SOAP to map UDP ports and
// turns every mapping into real iptables rules (DNAT + FORWARD), so the kernel,
// not a simulation, decides whether the mapped port is reachable from outside.
package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"
)

var (
	lanIP  = flag.String("lan", "192.168.1.1", "address on the LAN side")
	lanIf  = flag.String("lanif", "lanA", "LAN interface")
	wanIP  = flag.String("wan", "203.0.113.1", "public address reported to clients")
	wanIf  = flag.String("wanif", "wanA", "WAN interface")
	logTo  = flag.String("log", "", "append a line per mapping change here")
	ipt    string
	mu     sync.Mutex
	active = map[string]string{} // external port -> internal host:port
)

func main() {
	flag.Parse()
	ipt = "iptables"
	if p, err := exec.LookPath("iptables-legacy"); err == nil {
		ipt = p
	}
	ifi, err := net.InterfaceByName(*lanIf)
	if err != nil {
		log.Fatal(err)
	}
	go ssdp(ifi)
	mux := http.NewServeMux()
	mux.HandleFunc("/rootDesc.xml", desc)
	mux.HandleFunc("/ctl/IPConn", soap)
	srv := &http.Server{Addr: *lanIP + ":5000", Handler: mux}
	go func() { log.Fatal(srv.ListenAndServe()) }()

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGTERM, syscall.SIGINT)
	<-sig
	mu.Lock()
	for ext, to := range active {
		rules("-D", ext, to)
	}
	mu.Unlock()
}

func record(format string, a ...any) {
	line := fmt.Sprintf(format, a...)
	log.Print(line)
	if *logTo != "" {
		if f, err := os.OpenFile(*logTo, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644); err == nil {
			fmt.Fprintln(f, line)
			f.Close()
		}
	}
}

func rules(op, ext, to string) error {
	host, port, _ := strings.Cut(to, ":")
	dnat := []string{"-t", "nat", op, "PREROUTING", "-i", *wanIf, "-p", "udp", "--dport", ext, "-j", "DNAT", "--to-destination", to}
	if out, err := exec.Command(ipt, dnat...).CombinedOutput(); err != nil {
		return fmt.Errorf("%v: %s", err, out)
	}
	fwdOp := "-I"
	if op == "-D" {
		fwdOp = "-D"
	}
	fwd := []string{fwdOp, "FORWARD"}
	if fwdOp == "-I" {
		fwd = append(fwd, "1")
	}
	fwd = append(fwd, "-i", *wanIf, "-p", "udp", "-d", host, "--dport", port, "-j", "ACCEPT")
	if out, err := exec.Command(ipt, fwd...).CombinedOutput(); err != nil {
		return fmt.Errorf("%v: %s", err, out)
	}
	return nil
}

func ssdp(ifi *net.Interface) {
	group := &net.UDPAddr{IP: net.IPv4(239, 255, 255, 250), Port: 1900}
	conn, err := net.ListenMulticastUDP("udp4", ifi, group)
	if err != nil {
		log.Fatal(err)
	}
	buf := make([]byte, 2048)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			return
		}
		req := string(buf[:n])
		if !strings.HasPrefix(req, "M-SEARCH") {
			continue
		}
		st := "urn:schemas-upnp-org:device:InternetGatewayDevice:1"
		if !strings.Contains(req, "InternetGatewayDevice") && !strings.Contains(req, "rootdevice") && !strings.Contains(req, "ssdp:all") {
			continue
		}
		// Answer from the LAN address, as a router does.
		out, err := net.DialUDP("udp4", &net.UDPAddr{IP: net.ParseIP(*lanIP)}, from)
		if err != nil {
			continue
		}
		time.Sleep(20 * time.Millisecond)
		fmt.Fprintf(out, "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=120\r\nEXT:\r\nLOCATION: http://%s:5000/rootDesc.xml\r\nSERVER: fakeigd/1.0 UPnP/1.0\r\nST: %s\r\nUSN: uuid:fakeigd::%s\r\n\r\n", *lanIP, st, st)
		out.Close()
	}
}

func desc(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, `<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><specVersion><major>1</major><minor>0</minor></specVersion>
<device><deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType><friendlyName>fakeigd</friendlyName>
<deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:1</deviceType>
<deviceList><device><deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:1</deviceType>
<serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType><serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId>
<controlURL>/ctl/IPConn</controlURL></service></serviceList></device></deviceList></device></deviceList></device></root>`)
}

func field(body, name string) string {
	open, closeTag := "<"+name+">", "</"+name+">"
	i := strings.Index(body, open)
	if i < 0 {
		return ""
	}
	j := strings.Index(body[i:], closeTag)
	if j < 0 {
		return ""
	}
	return strings.TrimSpace(body[i+len(open) : i+j])
}

func soap(w http.ResponseWriter, r *http.Request) {
	b, _ := io.ReadAll(io.LimitReader(r.Body, 64<<10))
	body := string(b)
	action := r.Header.Get("SOAPAction")
	if i := strings.Index(action, "#"); i >= 0 {
		action = strings.Trim(action[i+1:], `"`)
	}
	fault := func(code int, desc string) {
		w.WriteHeader(500)
		fmt.Fprintf(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>%d</errorCode><errorDescription>%s</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>`, code, desc)
	}
	mu.Lock()
	defer mu.Unlock()
	switch action {
	case "GetExternalIPAddress":
		fmt.Fprintf(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetExternalIPAddressResponse xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1"><NewExternalIPAddress>%s</NewExternalIPAddress></u:GetExternalIPAddressResponse></s:Body></s:Envelope>`, *wanIP)
	case "AddPortMapping":
		ext, intPort, client := field(body, "NewExternalPort"), field(body, "NewInternalPort"), field(body, "NewInternalClient")
		if field(body, "NewProtocol") != "UDP" || ext == "" || intPort == "" || client == "" {
			fault(402, "Invalid Args")
			return
		}
		to := client + ":" + intPort
		if old, ok := active[ext]; ok && old != to {
			fault(718, "ConflictInMappingEntry")
			return
		}
		if _, ok := active[ext]; !ok {
			if err := rules("-A", ext, to); err != nil {
				log.Print(err)
				fault(501, "ActionFailed")
				return
			}
			active[ext] = to
		}
		record("ADD ext=%s to=%s lease=%s", ext, to, field(body, "NewLeaseDuration"))
		fmt.Fprint(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:AddPortMappingResponse xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1"/></s:Body></s:Envelope>`)
	case "DeletePortMapping":
		ext := field(body, "NewExternalPort")
		to, ok := active[ext]
		if !ok {
			fault(714, "NoSuchEntryInArray")
			return
		}
		rules("-D", ext, to)
		delete(active, ext)
		record("DEL ext=%s", ext)
		fmt.Fprint(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:DeletePortMappingResponse xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1"/></s:Body></s:Envelope>`)
	default:
		fault(401, "Invalid Action")
	}
}
