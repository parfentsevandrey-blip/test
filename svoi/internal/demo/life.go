package demo

import (
	"bytes"
	"context"
	"io"
	"math/rand"
	"net/netip"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
)

func bytesReader(b []byte) io.Reader { return bytes.NewReader(b) }

func servicesFrom(s [3]string) services.Service {
	return services.Service{Name: s[0], Addr: s[1], Description: s[2], Allow: []string{"*"}}
}

// life makes the demo feel alive: new chat messages and mail arrive, and the
// phone drops off the network for a while now and then.
func (d *Demo) life(ctx context.Context) {
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	phone, nas, hs, laptop := d.Devices["phone"].App, d.Devices["nas"].App, d.Devices["home-server"].App, d.Devices["laptop"].App
	lp := laptop.Node().ID()
	chatLines := []string{
		"Я уже еду домой 🚗", "Купить что-нибудь к чаю?", "Посмотри, какое небо сегодня!", "Позвони, когда освободишься",
		"Зарядку нашла, спасибо", "Футбол в 21:00, смотришь?", "Принесу хлеб и молоко",
	}
	mails := []struct{ subject, body string }{
		{"Диск почти заполнен", "На разделе «Фото» осталось 12% свободного места. Пора разобрать архив."},
		{"Еженедельный отчёт", "За неделю: 14 новых фотографий, 3 документа, 0 ошибок синхронизации."},
		{"Обновление безопасности", "Доступно обновление прошивки NAS. Установится автоматически ночью."},
	}
	cat := time.NewTicker(35 * time.Second)
	defer cat.Stop()
	outage := time.NewTimer(75 * time.Second)
	defer outage.Stop()
	phonePub := netip.MustParseAddr("203.0.113.3")
	pairs := [][2]netip.Addr{
		{phonePub, netip.MustParseAddr("203.0.113.10")},
		{phonePub, netip.MustParseAddr("203.0.113.1")},
	}
	block := func(on bool) {
		for _, p := range pairs {
			d.Net.Block(p[0], p[1], on)
		}
	}
	for {
		select {
		case <-ctx.Done():
			block(false)
			return
		case <-cat.C:
			switch rng.Intn(3) {
			case 0, 1:
				_, _ = phone.Mail().Send(mail.SendInput{Kind: "chat", To: []identity.ID{lp}, Body: chatLines[rng.Intn(len(chatLines))]})
			default:
				m := mails[rng.Intn(len(mails))]
				from := nas
				if rng.Intn(2) == 0 {
					from = hs
				}
				_, _ = from.Mail().Send(mail.SendInput{Kind: "mail", To: []identity.ID{lp}, Subject: m.subject, Body: m.body})
			}
		case <-outage.C:
			// The phone leaves the network (tunnel, airplane mode), then comes back.
			block(true)
			select {
			case <-ctx.Done():
				block(false)
				return
			case <-time.After(30 * time.Second):
			}
			block(false)
			outage.Reset(time.Duration(100+rng.Intn(60)) * time.Second)
		}
	}
}
