# MTR Traffic Addon — Założenia i Architektura

## Opis projektu

Addon do Minecraft Transit Railway (MTR), którego celem jest generowanie lekkiego i wydajnego ruchu drogowego w świecie Minecraft.

System będzie wykorzystywał istniejące:
- Railway Nodes,
- Railway Connectors,
- routing autobusów MTR,
- graph/path system MTR.

Addon NIE tworzy osobnego systemu dróg.
Zamiast tego rozszerza istniejącą infrastrukturę MTR o ruch drogowy.

Dzięki temu:
- istniejące sieci autobusowe automatycznie mogą obsługiwać ruch samochodowy,
- builderzy nie muszą tworzyć nowych node’ów,
- system pozostaje kompatybilny z innymi addonami autobusowymi.

---

# Główne założenia

## 1. Wspólny system infrastruktury

Samochody będą korzystać z:
- tych samych Railway Nodes,
- tych samych Railway Connectors,
- tego samego graph systemu,
co autobusy MTR.

Pojazdy drogowe nie będą jednak traktowane jako pociągi.
Będą posiadały własny system symulacji i renderingu.

---

## 2. Lekka symulacja ruchu

Addon nie ma być realistycznym symulatorem jazdy.

Celem jest:
- stworzenie żywego miasta,
- generowanie ruchu drogowego,
- wysoka wydajność,
- możliwość obsługi setek pojazdów jednocześnie.

Pojazdy:
- nie używają Minecraft AI,
- nie używają navmesha,
- nie analizują bloków świata,
- poruszają się wyłącznie po graphie MTR.

---

# System Track Types

Addon dodaje nowe typy tracków.

## Traffic Spawn Track

Track odpowiedzialny za generowanie pojazdów.

Funkcje:
- spawn pojazdów,
- konfiguracja natężenia ruchu,
- konfiguracja typów pojazdów,
- możliwość ustawienia limitów spawnów.

Przykładowe ustawienia:
- spawn interval,
- max vehicles,
- vehicle pool,
- traffic density,
- spawn chance.

Przykład konfiguracji:

```json
{
  "spawnRate": 0.5,
  "maxVehicles": 40,
  "vehicles": [
    "sedan",
    "taxi",
    "van"
  ]
}
```

---

## Traffic Despawn Track

Track odpowiedzialny za usuwanie pojazdów.

Funkcje:
- despawn pojazdu po dotarciu do celu,
- możliwość zliczania statystyk ruchu,
- możliwość przyszłego systemu analytics.

Pojazd po dojechaniu do Despawn Track:
- kończy trasę,
- zostaje usunięty,
- zwalnia slot ruchu.

---

# Routing i pathfinding

## Podstawowy system

Po wygenerowaniu pojazdu:

1. wybierany jest losowy Traffic Despawn Track,
2. system oblicza trasę po graphie MTR,
3. pojazd rozpoczyna ruch.

Routing wykorzystuje istniejący graph/path system MTR.

Addon nie tworzy nowego systemu pathfindingu.

---

## Typy routingu

### Random Routing

Pojazd wybiera losową trasę.

Cel:
- naturalnie wyglądający ruch,
- brak powtarzalności,
- prostota.

---

### Weighted Routing (planowane)

Możliwość ustawienia priorytetów dróg.

Przykłady:
- główne ulice częściej wybierane,
- drogi przemysłowe preferowane przez trucki,
- bus lanes preferowane przez autobusy.

---

# System ruchu pojazdów

## Movement System

Pojazd przechowuje:
- aktualny connector,
- distance along connector,
- speed,
- target node.

Pojazdy poruszają się po spline connectorów MTR.

Dzięki temu:
- ruch jest płynny,
- nie jest wymagana fizyka Minecraft,
- system jest bardzo wydajny.

---

## Speed System

Planowane funkcje:
- speed limits,
- acceleration,
- deceleration,
- minimal spacing.

---

## Vehicle Spacing

System zachowywania odstępu między pojazdami.

Przykład:

```text
Jeżeli odległość od pojazdu z przodu jest zbyt mała:
→ pojazd zwalnia.
```

System ma być prosty i wydajny.

---

# Kolizje

## MVP

Brak pełnych kolizji.

Pojazdy:
- nie zderzają się fizycznie,
- mogą częściowo ghostować,
- używają wyłącznie spacing system.

Powód:
- wydajność,
- prostota,
- możliwość obsługi dużej ilości pojazdów.

---

## Planowane rozszerzenia

Możliwe przyszłe funkcje:
- queue system,
- intersection reservation,
- traffic lights,
- lane priorities.

---

# Resource Pack Vehicle System

Addon będzie obsługiwał modele pojazdów przez Resource Pack.

## Założenia

Każdy pojazd może posiadać:
- model,
- tekstury,
- animacje,
- wheel offsets,
- długość,
- typ.

---

## Vehicle Definition JSON

Przykład:

```json
{
  "id": "sedan_01",
  "type": "car",
  "length": 4.2,
  "maxSpeed": 60,
  "spawnWeight": 10
}
```

---

## Typy pojazdów

Planowane typy:
- sedans,
- hatchbacks,
- SUVs,
- vans,
- trucks,
- buses,
- taxis,
- emergency vehicles.

---

# Integracja z autobusami

## Wspólna infrastruktura

Autobusy oraz samochody mogą korzystać z:
- tych samych node’ów,
- tych samych connectorów,
- tych samych dróg.

---

## Możliwe przyszłe funkcje

- bus lanes,
- vehicle priorities,
- autobusy ignorujące część traffic rules,
- wspólny routing API.

---

# Wydajność

## Główne założenie

Pojazdy NIE są pełnymi Minecraft entities.

Pojazdy są:
- lightweight traffic objects,
- renderowanymi obiektami,
- prostą symulacją ruchu.

---

## Synchronizacja multiplayer

Server:
- logika ruchu,
- routing,
- pozycja logiczna.

Client:
- rendering,
- interpolation,
- animacje.

---

## Chunk Optimization

Planowane funkcje:
- uproszczona symulacja poza chunkami,
- despawn dalekich pojazdów,
- ograniczenia aktywnego ruchu.

---

# Plan rozwoju

# Etap 1 — MVP

Funkcje:
- Traffic Spawn Track,
- Traffic Despawn Track,
- prosty movement system,
- random routing,
- jeden typ pojazdu,
- podstawowy rendering.

Cel:
- działający ruch drogowy,
- test wydajności,
- proof of concept.

---

# Etap 2 — Rozszerzony ruch

Funkcje:
- wiele modeli pojazdów,
- spacing system,
- speed limits,
- weighted routing,
- konfiguracja traffic density.

---

# Etap 3 — Zaawansowany traffic

Funkcje:
- traffic lights,
- queue system,
- intersection priorities,
- lane logic,
- emergency vehicle priority.

---

# Etap 4 — Ecosystem API

Funkcje:
- public traffic API,
- integracja z addonami autobusów,
- custom vehicle support,
- external addon compatibility.

---

# Potencjalne dodatkowe funkcje

## Dynamic Traffic Density

Natężenie ruchu zależne od:
- czasu dnia,
- lokalizacji,
- typu dzielnicy.

Przykłady:
- więcej ruchu rano,
- mniej ruchu nocą,
- więcej trucków w industrial areas.

---

## Vehicle Categories

Możliwość przypisywania pojazdów do kategorii:
- residential,
- commercial,
- industrial,
- public transport,
- emergency.

---

## Statistical System

Możliwe statystyki:
- ilość pojazdów,
- średni czas przejazdu,
- obciążenie dróg,
- traffic heatmaps.

---

# Główny cel projektu

Celem addona jest:

- tworzenie żywego miasta,
- automatyczny ruch drogowy,
- wysoka wydajność,
- łatwa integracja z istniejącymi mapami MTR,
- prostota konfiguracji dla builderów.

Addon ma zapewniać:
- believable city ambience,
- dużą skalę ruchu,
- minimalny wpływ na TPS serwera.

