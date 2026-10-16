#!/usr/bin/env bash
# REA audit of every resource we read (FSK excluded today: 429). One visit per page, 25 s between visits.
cd "$(dirname "$0")/.."
A=audit
run() { python3 -I lib/audit_site.py "$1" "$2" "$A" "${3:-}" >> $A/audit.out 2>&1 || echo "FAILED $1" >> $A/audit.out; sleep 25; }
run highlife "https://highlife.ru/search"
run dreamriva "https://dreamriva.ru/flats/"
run lsr_zilart "https://www.lsr.ru/msk/kvartiry-v-novostroikah/zilart/"
python3 -I lib/audit_site.py lsr_zilart_more "https://www.lsr.ru/msk/kvartiry-v-novostroikah/zilart/" $A "Показать ещё" >> $A/audit.out 2>&1; sleep 25
run granelle_object "https://granelle.ru/objects/mypriority-paveletskaya/"
run granelle_flat "https://granelle.ru/flats/mypriority-paveletskaya/62515/"
run forma_soul "https://forma.ru/market/projects/soul"
run forma_flat "https://forma.ru/market/flat/932340"
run ndr_object "https://наш.дом.рф/сервисы/каталог-новостроек/объект/64096"
run cian_zk "https://zhk-simvol-i.cian.ru/"
run cian_offer "https://www.cian.ru/sale/flat/294475361/"
run cian_search "https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&newobject%5B0%5D=7231&offer_type=flat&sort=price_object_order&p=1"
echo ALLDONE >> $A/audit.out
