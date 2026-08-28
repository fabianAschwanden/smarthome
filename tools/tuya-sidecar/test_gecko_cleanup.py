#!/usr/bin/env python3
"""
Regressionstest: Werden die Spa-Verbindungen nach jeder Abfrage geschlossen?

Anlass (August 2026): 'async with' auf dem SpaMan ruft nur __aexit__, und das beendet
lediglich die Tasks. Die UDP-Verbindungen zum Spa blieben offen - rund zwei Sockets je
Abfrage. Nach wenigen Stunden hatte der Sidecar 180 offene Sockets und wurde von seinem
Speicherlimit erschlagen, alle vier bis sechs Stunden.

Der zweite Fall ist der wichtigere: Laeuft die Abfrage in den Timeout, wird die innere
Coroutine abgebrochen. Ein Aufraeumen INNERHALB davon kaeme nie zum Zug - ausgerechnet
beim nicht erreichbaren Spa bliebe der Socket liegen.

Laeuft ohne Fremdpakete:  python3 tools/tuya-sidecar/test_gecko_cleanup.py
"""
import asyncio
import importlib.util
import pathlib
import sys
import types

# tinytuya wird beim Import gebraucht, aber hier nicht benutzt.
sys.modules.setdefault("tinytuya", types.ModuleType("tinytuya"))

_PFAD = pathlib.Path(__file__).with_name("sidecar.py")
_SPEC = importlib.util.spec_from_file_location("sidecar_under_test", _PFAD)
sidecar = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(sidecar)


class FakeMan:
    """SpaMan-Attrappe, die mitschreibt, ob sie geschlossen wurde."""

    def __init__(self, *_args):
        self.reset_called = False

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_exc):
        return None

    async def async_reset(self):
        self.reset_called = True


def _mit_attrappe(facade_factory, timeout=0.3):
    erzeugte = []

    def klasse():
        def bauen(*_args):
            man = FakeMan()
            erzeugte.append(man)
            return man

        return bauen

    sidecar._gecko_man_class = klasse
    sidecar._gecko_facade = facade_factory
    sidecar._gecko_snapshot = lambda _facade: {"ok": True}
    sidecar.GECKO_TIMEOUT = timeout
    return erzeugte


def test_schliesst_nach_erfolgreicher_abfrage():
    async def facade(*_args):
        return object()

    erzeugte = _mit_attrappe(facade)
    sidecar._gecko_run("1.2.3.4", None, "Spa", None)
    assert erzeugte[-1].reset_called, "Verbindung nach erfolgreicher Abfrage nicht geschlossen"


def test_schliesst_auch_wenn_das_spa_nicht_antwortet():
    async def facade(*_args):
        await asyncio.sleep(10)

    erzeugte = _mit_attrappe(facade)
    try:
        sidecar._gecko_run("1.2.3.4", None, "Spa", None)
        raise AssertionError("Timeout haette einen Fehler liefern muessen")
    except RuntimeError as exc:
        assert "antwortet nicht" in str(exc)
    assert erzeugte[-1].reset_called, "Verbindung nach Timeout nicht geschlossen"


def test_baut_die_klasse_nur_einmal():
    # Eine Klasse je Anfrage zu definieren erzeugt bei jedem Abruf ein neues Typ-Objekt
    # samt Methodentabellen - unnoetig, wenn sich nichts daran unterscheidet.
    stub = types.ModuleType("geckolib")
    stub.GeckoAsyncSpaMan = type("GeckoAsyncSpaMan", (), {})
    stub.GeckoSpaEvent = type("GeckoSpaEvent", (), {})
    sys.modules["geckolib"] = stub
    sidecar._gecko_man_cls = None

    assert sidecar._gecko_man_class() is sidecar._gecko_man_class()


if __name__ == "__main__":
    fehler = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"  ok   {name}")
            except Exception as exc:  # noqa: BLE001
                fehler += 1
                print(f"  FEHL {name}: {exc}")
    print("bestanden" if not fehler else f"{fehler} fehlgeschlagen")
    sys.exit(1 if fehler else 0)
