#!/usr/bin/env python3
"""
Texts added in Speech Split 3.0, in one place for all three languages.

    python3 tools/strings3.py

writes them to the web version (docs/app/strings3.js) and the apps
(app/src/main/java/no/srrlsm/speechsplit/core/Strings3.kt).

A plain string is a fixed text. "{0}", "{1}" are filled in by the app.
A pair ["one", "many"] is a count: the first when the number {0} is 1, otherwise the second.
"""
import json, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent

S = {
    # --- Meeting mode ---------------------------------------------------------
    "meetingMode": {"en": "Meeting mode", "no": "Møtemodus", "de": "Versammlungsmodus"},
    "meetingIntro": {
        "en": "For the chairman: follow the time of the whole meeting, part by part.",
        "no": "For ordstyreren: følg tiden for hele møtet, del for del.",
        "de": "Für den Vorsitzenden: die Zeit der ganzen Versammlung im Blick, Teil für Teil.",
    },
    "meetingMidweek": {"en": "Midweek", "no": "Midtukemøtet", "de": "Unter der Woche"},
    "meetingWeekend": {"en": "Weekend", "no": "Søndagsmøtet", "de": "Wochenende"},
    "meetingMidweekTitle": {"en": "Midweek meeting · {0}", "no": "Midtukemøtet · {0}", "de": "Versammlung unter der Woche · {0}"},
    "meetingWeekendTitle": {"en": "Weekend meeting", "no": "Søndagsmøtet", "de": "Wochenendversammlung"},
    "meetingStart": {"en": "Start meeting", "no": "Start møtet", "de": "Versammlung starten"},
    "meetingThisWeek": {"en": "This week", "no": "Denne uken", "de": "Diese Woche"},
    "meetingPrevWeek": {"en": "Previous week", "no": "Forrige uke", "de": "Vorige Woche"},
    "meetingNextWeek": {"en": "Next week", "no": "Neste uke", "de": "Nächste Woche"},
    "meetingNoWeek": {
        "en": "There's no program for this week yet. It's added a few days after a new workbook comes out.",
        "no": "Programmet for denne uken er ikke lagt inn ennå. Det kommer noen dager etter at et nytt arbeidshefte er ute.",
        "de": "Für diese Woche gibt es noch kein Programm. Es kommt ein paar Tage nach einem neuen Arbeitsheft.",
    },
    "meetingNoneThisWeek": {
        "en": "The workbook has no midweek meeting this week (for example the week of the Memorial or a convention).",
        "no": "Arbeidsheftet har ikke noe midtukemøte denne uken (for eksempel uken med minnehøytiden eller et stevne).",
        "de": "Das Arbeitsheft hat diese Woche keine Versammlung unter der Woche (zum Beispiel in der Woche des Gedächtnismahls oder eines Kongresses).",
    },
    "meetingLoading": {"en": "Loading the program…", "no": "Henter programmet …", "de": "Programm wird geladen …"},
    "meetingOffline": {
        "en": "Couldn't check for a newer program. Showing the saved one.",
        "no": "Fikk ikke sjekket om det finnes et nyere program. Viser det lagrede.",
        "de": "Konnte nicht nach einem neueren Programm suchen. Das gespeicherte wird angezeigt.",
    },
    "meetingNoData": {
        "en": "Couldn't load the program. Check the internet connection and try again.",
        "no": "Fikk ikke hentet programmet. Sjekk internettforbindelsen og prøv igjen.",
        "de": "Programm konnte nicht geladen werden. Prüfe die Internetverbindung und versuche es erneut.",
    },
    "meetingNoLanguage": {
        "en": "There's no program in this language yet. Pick another language at the top.",
        "no": "Det finnes ikke noe program på dette språket ennå. Velg et annet språk øverst.",
        "de": "In dieser Sprache gibt es noch kein Programm. Wähle oben eine andere Sprache.",
    },
    "meetingRetry": {"en": "Try again", "no": "Prøv igjen", "de": "Erneut versuchen"},
    "meetingLanguage": {"en": "Program language", "no": "Språk for programmet", "de": "Sprache des Programms"},
    "meetingWeekendNote": {
        "en": "The same every week. The talk's theme varies from congregation to congregation.",
        "no": "Det samme hver uke. Temaet for foredraget varierer fra menighet til menighet.",
        "de": "Jede Woche gleich. Das Thema des Vortrags ist von Versammlung zu Versammlung verschieden.",
    },
    "meetingTotal": {"en": "{0} program + {1} spare", "no": "{0} program + {1} reservetid", "de": "{0} Programm + {1} Reserve"},
    "meetingLength": {"en": "{0} in total", "no": "{0} totalt", "de": "{0} insgesamt"},
    "bufferLeft": {"en": "SPARE TIME · {0} LEFT", "no": "RESERVETID · {0} IGJEN", "de": "RESERVEZEIT · NOCH {0}"},
    "bufferUsed": {"en": "SPARE TIME USED UP · {0} OVER", "no": "RESERVETIDEN ER BRUKT OPP · {0} OVER", "de": "RESERVEZEIT AUFGEBRAUCHT · {0} DRÜBER"},
    "songPrayerN": {"en": "Song {0} and Prayer", "no": "Sang {0} og bønn", "de": "Lied {0} und Gebet"},
    "songN": {"en": "Song {0}", "no": "Sang {0}", "de": "Lied {0}"},
    "songPrayer": {"en": "Song and Prayer", "no": "Sang og bønn", "de": "Lied und Gebet"},
    "song": {"en": "Song", "no": "Sang", "de": "Lied"},
    "openingComments": {"en": "Opening Comments", "no": "Innledende kommentarer", "de": "Einleitende Worte"},
    "concludingComments": {"en": "Concluding Comments", "no": "Avsluttende kommentarer", "de": "Schlussworte"},
    "publicTalk": {"en": "Public Talk", "no": "Offentlig foredrag", "de": "Öffentlicher Vortrag"},
    "watchtowerStudy": {"en": "Watchtower Study", "no": "Vakttårnstudiet", "de": "Wachtturm-Studium"},
    "meetingEndedEarly": {"en": "The meeting ended {0} early.", "no": "Møtet sluttet {0} før tiden.", "de": "Die Versammlung endete {0} früher."},
    "meetingEndedLate": {"en": "The meeting ran {0} over.", "no": "Møtet gikk {0} over tiden.", "de": "Die Versammlung ging {0} über die Zeit."},
    "meetingEndedOnTime": {"en": "The meeting ended right on time.", "no": "Møtet sluttet akkurat på tiden.", "de": "Die Versammlung endete genau pünktlich."},
    "meetingHistoryTitle": {"en": "Meeting history", "no": "Møtehistorikk", "de": "Versammlungsverlauf"},
    "meetingEndsAt": {"en": "DONE AROUND {0}", "no": "FERDIG CA. KL. {0}", "de": "ENDE CA. {0} UHR"},

    # --- Sub-parts of a meeting part (3.1) ----------------------------------------
    "subpartsHint": {
        "en": "Tap a part to split it into sub-parts, for example your own talk.",
        "no": "Trykk på en del for å dele den opp i underdeler, for eksempel din egen tale.",
        "de": "Tippe auf einen Teil, um ihn in Unterteile aufzuteilen, zum Beispiel deine eigene Aufgabe.",
    },
    "subpartsCount": {"en": ["1 sub-part", "{0} sub-parts"], "no": ["1 underdel", "{0} underdeler"], "de": ["1 Unterteil", "{0} Unterteile"]},
    "subpartAdd": {"en": "Sub-part", "no": "Underdel", "de": "Unterteil"},
    "subpartFromSpeech": {"en": "From a speech", "no": "Hent fra tale", "de": "Aus einer Rede"},
    "subpartClear": {"en": "Remove all", "no": "Fjern alle", "de": "Alle entfernen"},
    "subpartName": {"en": "Name", "no": "Navn", "de": "Name"},
    "subpartTime": {"en": "Time", "no": "Tid", "de": "Zeit"},
    "subpartNew": {"en": "New sub-part", "no": "Ny underdel", "de": "Neuer Unterteil"},
    "subpartRemove": {"en": "Remove sub-part", "no": "Fjern underdelen", "de": "Unterteil entfernen"},
    "subpartsSum": {"en": "Sub-parts: {0} of {1}", "no": "Underdeler: {0} av {1}", "de": "Unterteile: {0} von {1}"},
    "subpartsLeft": {
        "en": "The remaining {0} is added to the last sub-part.",
        "no": "De siste {0} legges til den siste underdelen.",
        "de": "Die restlichen {0} kommen zum letzten Unterteil.",
    },
    "subpartsOver": {
        "en": "The sub-parts are {0} longer than the part.",
        "no": "Underdelene er {0} lengre enn delen.",
        "de": "Die Unterteile sind {0} länger als der Teil.",
    },
    "subpartPick": {
        "en": "Pick the speech whose parts you want to use here.",
        "no": "Velg talen du vil hente delene fra.",
        "de": "Wähle die Rede, deren Teile du hier verwenden willst.",
    },
    "subpartNoSpeeches": {"en": "You have no saved speeches yet.", "no": "Du har ingen lagrede taler ennå.", "de": "Du hast noch keine gespeicherten Reden."},

    # --- Spare time (buffer) for a speech (3.1) -----------------------------------
    "bufferLabel": {"en": "Spare time (buffer)", "no": "Reservetid (buffer)", "de": "Reservezeit (Puffer)"},
    "bufferHint": {
        "en": "Optional. If a part runs over, the spare time is used first. The speech is only over time once it's used up.",
        "no": "Valgfritt. Går en del over tiden, brukes reservetiden først. Talen er først over tiden når den er brukt opp.",
        "de": "Optional. Läuft ein Teil über, wird zuerst die Reservezeit verbraucht. Die Rede ist erst über der Zeit, wenn sie aufgebraucht ist.",
    },
    "bufferPlus": {"en": "+ {0} spare time", "no": "+ {0} reservetid", "de": "+ {0} Reservezeit"},
    "praiseWithinBuffer": {
        "en": "You stayed within the spare time (used {0}).",
        "no": "Du holdt deg innenfor reservetiden (brukte {0}).",
        "de": "Du bist in der Reservezeit geblieben ({0} verbraucht).",
    },

    # --- Big clock on a second screen (3.1, Windows) ------------------------------
    "secondScreenTitle": {"en": "Big clock on screen 2", "no": "Stor klokke på skjerm 2", "de": "Große Uhr auf Bildschirm 2"},
    "secondScreenDesc": {
        "en": "With a second screen connected, it shows the part's name and a big clock while the timer runs. This window stays as usual. Shortcut: S.",
        "no": "Når en ekstra skjerm er koblet til, viser den navnet på delen og en stor klokke mens timeren går. Dette vinduet er som vanlig. Snarvei: S.",
        "de": "Ist ein zweiter Bildschirm angeschlossen, zeigt er den Namen des Teils und eine große Uhr, während der Timer läuft. Dieses Fenster bleibt wie gewohnt. Kürzel: S.",
    },
    "secondScreenNone": {
        "en": "No second screen found. Connect one and try again.",
        "no": "Fant ingen skjerm 2. Koble til en skjerm og prøv igjen.",
        "de": "Kein zweiter Bildschirm gefunden. Schließe einen an und versuche es erneut.",
    },

    # --- Practice -------------------------------------------------------------
    "modePractice": {"en": "Practice", "no": "Øving", "de": "Übung"},
    "modeLive": {"en": "Real speech", "no": "Ekte tale", "de": "Echte Rede"},
    "modeDesc": {
        "en": "Real speeches are marked in the history, so you can compare them with practice.",
        "no": "Ekte taler merkes i historikken, så du kan sammenligne dem med øvingen.",
        "de": "Echte Reden werden im Verlauf markiert, damit du sie mit der Übung vergleichen kannst.",
    },
    "liveBadge": {"en": "Real", "no": "Ekte", "de": "Echt"},
    "filterAll": {"en": "All", "no": "Alle", "de": "Alle"},
    "drillTitle": {"en": "Practise one part", "no": "Øv på én del", "de": "Einen Teil üben"},
    "drillPick": {
        "en": "Pick the part you want to run again and again. These rounds aren't saved in the history.",
        "no": "Velg delen du vil øve på om og om igjen. Rundene lagres ikke i historikken.",
        "de": "Wähle den Teil, den du immer wieder üben willst. Diese Runden werden nicht im Verlauf gespeichert.",
    },
    "drillAgain": {"en": "Again", "no": "Igjen", "de": "Nochmal"},
    "drillAttempts": {"en": "ATTEMPTS", "no": "FORSØK", "de": "VERSUCHE"},
    "drillAttempt": {"en": "Attempt {0}", "no": "Forsøk {0}", "de": "Versuch {0}"},

    # --- Summary ----------------------------------------------------------------
    "praiseTitle": {"en": "WENT WELL", "no": "DETTE GIKK BRA", "de": "DAS LIEF GUT"},
    "notesTitle": {"en": "WORTH NOTING", "no": "VERDT Å MERKE SEG", "de": "BEACHTENSWERT"},
    "praisePerfect": {"en": "Right on the planned total.", "no": "Du traff totaltiden på sekundet.", "de": "Genau auf die geplante Gesamtzeit."},
    "praiseTotalClose": {"en": "Only {0} off the planned total.", "no": "Bare {0} unna totaltiden.", "de": "Nur {0} neben der Gesamtzeit."},
    "praiseUnder": {"en": "You stayed within the time, with {0} to spare.", "no": "Du holdt deg innenfor tiden, med {0} til gode.", "de": "Du bist im Zeitrahmen geblieben, mit {0} Luft."},
    "praiseSegments": {"en": "{0} of {1} parts were on time.", "no": "{0} av {1} deler var på tid.", "de": "{0} von {1} Teilen waren in der Zeit."},
    "praiseExact": {"en": "“{0}” hit its time to the second.", "no": "«{0}» traff tiden på sekundet.", "de": "„{0}“ traf die Zeit auf die Sekunde."},
    "praiseBetter": {"en": "{0} closer to the plan than last time.", "no": "{0} nærmere planen enn forrige gang.", "de": "{0} näher am Plan als beim letzten Mal."},
    "noteOver": {"en": "“{0}” ran {1} over time.", "no": "«{0}» gikk {1} over tiden.", "de": "„{0}“ lief {1} über die Zeit."},
    "noteUnder": {"en": "“{0}” was {1} under time.", "no": "«{0}» var {1} under tiden.", "de": "„{0}“ war {1} unter der Zeit."},
    "noteMore": {"en": ["…and 1 more part.", "…and {0} more parts."], "no": ["… og 1 del til.", "… og {0} deler til."], "de": ["… und 1 weiterer Teil.", "… und {0} weitere Teile."]},
    "compareLast": {"en": "Last time {0} · this time {1}", "no": "Forrige gang {0} · nå {1}", "de": "Letztes Mal {0} · diesmal {1}"},
    "verdictOnTime": {"en": "On time", "no": "På tid", "de": "In der Zeit"},
    "verdictBitOver": {"en": "A bit over", "no": "Litt over", "de": "Etwas drüber"},
    "verdictOver": {"en": "Over", "no": "Over", "de": "Drüber"},
    "verdictBitUnder": {"en": "A bit under", "no": "Litt under", "de": "Etwas drunter"},
    "verdictUnder": {"en": "Under", "no": "Under", "de": "Drunter"},

    # --- Folders ----------------------------------------------------------------
    "newFolder": {"en": "New folder", "no": "Ny mappe", "de": "Neuer Ordner"},
    "folderName": {"en": "Folder name", "no": "Navn på mappen", "de": "Name des Ordners"},
    "renameFolder": {"en": "Rename folder", "no": "Gi mappen nytt navn", "de": "Ordner umbenennen"},
    "deleteFolder": {"en": "Delete folder", "no": "Slett mappen", "de": "Ordner löschen"},
    "deleteFolderTitle": {"en": "Delete “{0}”?", "no": "Slette mappen «{0}»?", "de": "Ordner „{0}“ löschen?"},
    "deleteFolderText": {
        "en": "The speeches in it go back to the library. They aren't deleted.",
        "no": "Talene i mappen flyttes tilbake til biblioteket. De slettes ikke.",
        "de": "Die Reden darin kommen zurück in die Bibliothek. Sie werden nicht gelöscht.",
    },
    "moveToFolder": {"en": "Move to folder", "no": "Flytt til mappe", "de": "In Ordner verschieben"},
    "noFolder": {"en": "No folder", "no": "Ingen mappe", "de": "Kein Ordner"},
    "folderEmpty": {
        "en": "This folder is empty. Create a speech here, or move one in with ⋮ → Move to folder.",
        "no": "Mappen er tom. Lag en ny tale her, eller flytt en hit med ⋮ → Flytt til mappe.",
        "de": "Der Ordner ist leer. Erstelle hier eine Rede oder verschiebe eine mit ⋮ → In Ordner verschieben.",
    },
    "folderCount": {"en": ["1 speech", "{0} speeches"], "no": ["1 tale", "{0} taler"], "de": ["1 Rede", "{0} Reden"]},
    "movedTo": {"en": "Moved to {0}", "no": "Flyttet til {0}", "de": "Verschoben nach {0}"},
    "movedOut": {"en": "Moved to the library", "no": "Flyttet til biblioteket", "de": "In die Bibliothek verschoben"},
    "create": {"en": "Create", "no": "Opprett", "de": "Erstellen"},
    "save": {"en": "Save", "no": "Lagre", "de": "Speichern"},

    # --- Introduction and what's new ------------------------------------------
    "tutorialShow": {"en": "Show the introduction", "no": "Vis veiledningen", "de": "Einführung anzeigen"},
    "tutSkip": {"en": "Skip", "no": "Hopp over", "de": "Überspringen"},
    "tutNext": {"en": "Next", "no": "Neste", "de": "Weiter"},
    "tutDone": {"en": "Get started", "no": "Kom i gang", "de": "Los geht's"},
    "tut1Title": {"en": "Welcome to Speech Split", "no": "Velkommen til Speech Split", "de": "Willkommen bei Speech Split"},
    "tut1Text": {
        "en": "Split your talk into parts and give each part a time. You'll always see whether you're ahead or behind.",
        "no": "Del talen inn i deler, og gi hver del en tid. Da ser du hele veien om du ligger foran eller bak.",
        "de": "Teile deine Rede in Abschnitte und gib jedem eine Zeit. So siehst du immer, ob du vor oder hinter dem Plan liegst.",
    },
    "tut2Title": {"en": "While you speak", "no": "Mens du taler", "de": "Während du sprichst"},
    "tut2Text": {
        "en": "Tap Next when you move on. The big clock shows what's left of the part, and the pill at the top shows where you stand overall.",
        "no": "Trykk Neste når du går videre. Den store klokka viser hvor mye som er igjen av delen, og merket øverst viser hvordan du ligger an totalt.",
        "de": "Tippe auf Weiter, wenn du zum nächsten Abschnitt gehst. Die große Uhr zeigt die Restzeit des Abschnitts, die Markierung oben, wo du insgesamt stehst.",
    },
    "tut3Title": {"en": "Practise and improve", "no": "Øv og bli bedre", "de": "Üben und besser werden"},
    "tut3Text": {
        "en": "Choose Practice or Real speech before you start. Afterwards you get a summary, and the history shows where you tend to run over.",
        "no": "Velg Øving eller Ekte tale før du starter. Etterpå får du en oppsummering, og historikken viser hvor du pleier å gå over.",
        "de": "Wähle vor dem Start Übung oder Echte Rede. Danach bekommst du eine Zusammenfassung, und der Verlauf zeigt, wo du oft überziehst.",
    },
    "tut4Title": {"en": "Meeting mode", "no": "Møtemodus", "de": "Versammlungsmodus"},
    "tut4Text": {
        "en": "For the chairman: this week's program for the midweek or weekend meeting loads by itself, so you can follow the time of the whole meeting.",
        "no": "For ordstyreren: ukens program for midtukemøtet eller søndagsmøtet lastes inn av seg selv, så du kan følge tiden for hele møtet.",
        "de": "Für den Vorsitzenden: Das Programm der Woche für die Versammlung unter der Woche oder am Wochenende lädt von selbst, damit du die Zeit der ganzen Versammlung im Blick hast.",
    },
    "tut5Title": {"en": "Folders and sharing", "no": "Mapper og deling", "de": "Ordner und Teilen"},
    "tut5Text": {
        "en": "Keep your speeches in folders. Export one speech or the whole library as a file and open it on another device.",
        "no": "Samle talene dine i mapper. Eksporter en tale eller hele biblioteket som en fil, og åpne den på en annen enhet.",
        "de": "Ordne deine Reden in Ordnern. Exportiere eine Rede oder die ganze Bibliothek als Datei und öffne sie auf einem anderen Gerät.",
    },
    "whatsNewTitle": {"en": "New in version {0}", "no": "Nytt i versjon {0}", "de": "Neu in Version {0}"},
    "whatsNewItems": {
        "en": "Sub-parts in meeting mode: tap a part to split it up, or bring in one of your own speeches, so you can chair the meeting and give your talk at the same time.\n"
              "Spare time (buffer) for a speech: set it when you edit the speech.\n"
              "The program language in meeting mode is now a button at the top.\n"
              "Windows: a big clock on a second screen while this window stays as usual.",
        "no": "Underdeler i møtemodus: trykk på en del for å dele den opp, eller hent inn en av dine egne taler, så du kan lede møtet og holde talen din samtidig.\n"
              "Reservetid (buffer) for en tale: du setter den når du redigerer talen.\n"
              "Språket for programmet i møtemodus er nå en knapp øverst.\n"
              "Windows: stor klokke på en ekstra skjerm, mens dette vinduet er som vanlig.",
        "de": "Unterteile im Versammlungsmodus: Tippe auf einen Teil, um ihn aufzuteilen, oder hole eine deiner eigenen Reden dazu. So kannst du die Versammlung leiten und zugleich deine Aufgabe halten.\n"
              "Reservezeit (Puffer) für eine Rede: Du stellst sie beim Bearbeiten der Rede ein.\n"
              "Die Sprache des Programms im Versammlungsmodus ist jetzt ein Knopf oben.\n"
              "Windows: eine große Uhr auf einem zweiten Bildschirm, während dieses Fenster wie gewohnt bleibt.",
    },
    "allPatchNotes": {"en": "All patch notes", "no": "Alle patch notes", "de": "Alle Versionshinweise"},
    "ok": {"en": "OK", "no": "OK", "de": "OK"},

    # --- Import (documents removed) ---------------------------------------------
    "importOnlyFiles": {
        "en": "Only Speech Split files (.speechsplit) can be opened.",
        "no": "Bare Speech Split-filer (.speechsplit) kan åpnes.",
        "de": "Nur Speech-Split-Dateien (.speechsplit) können geöffnet werden.",
    },
    "importFileDesc": {
        "en": "A speech or library backup saved with Export (.speechsplit).",
        "no": "En tale eller sikkerhetskopi lagret med Eksporter (.speechsplit).",
        "de": "Eine Rede oder Sicherung, gespeichert mit Exportieren (.speechsplit).",
    },
    "importPasteHint": {"en": "Paste an exported speech", "no": "Lim inn en eksportert tale", "de": "Füge eine exportierte Rede ein"},
    "importPasteOnly": {
        "en": "Paste the text of an exported speech.",
        "no": "Lim inn teksten fra en eksportert tale.",
        "de": "Füge den Text einer exportierten Rede ein.",
    },
    "importDropFile": {
        "en": "Tip: you can also drag a .speechsplit file straight into the app.",
        "no": "Tips: du kan også dra en .speechsplit-fil rett inn i appen.",
        "de": "Tipp: Du kannst eine .speechsplit-Datei auch direkt in die App ziehen.",
    },
}

LANGS = ["en", "no", "de"]


def params(text):
    return sorted(set(int(m) for m in re.findall(r"\{(\d)\}", text)))


def kind(key):
    v = S[key]["en"]
    if isinstance(v, list):
        return "plural"
    return "fn" if params(v) else "text"


def js():
    out = ["// Texts added in 3.0. Made by tools/strings3.py: change them there, then run it.",
           "// A function takes the numbers or names that go into the text.",
           "(function () {", "  function fill(t, a) { return t.replace(/\\{(\\d)\\}/g, function (_, i) { return a[+i]; }); }",
           "  window.STR3 = {"]
    for lang in LANGS:
        out.append(f"    {json.dumps(lang)}: {{")
        for key in S:
            v = S[key][lang]
            k = kind(key)
            if k == "text":
                out.append(f"      {key}: {json.dumps(v, ensure_ascii=False)},")
            elif k == "fn":
                out.append(f"      {key}: function () {{ return fill({json.dumps(v, ensure_ascii=False)}, arguments); }},")
            else:
                out.append(f"      {key}: function (n) {{ return fill(n === 1 ? {json.dumps(v[0], ensure_ascii=False)} : {json.dumps(v[1], ensure_ascii=False)}, [n]); }},")
        out.append("    },")
    out += ["  };", "})();", ""]
    (ROOT / "docs/app/strings3.js").write_text("\n".join(out))


def kt_str(s):
    return json.dumps(s, ensure_ascii=False).replace("$", "\\$")


def kt_template(s, names):
    # "{0} of {1}" -> "$a of $b"
    s = json.dumps(s, ensure_ascii=False).replace("$", "\\$")
    for i, n in enumerate(names):
        s = s.replace("{" + str(i) + "}", "${" + n + "}")
    return s


def kt():
    names = "abcd"
    out = ["package no.srrlsm.speechsplit.core", "",
           "// ============================================================================",
           "// TEXTS ADDED IN 3.0",
           "// Made by tools/strings3.py, which also writes them for the web version.",
           "// Change them there and run it, so the apps and the web version stay the same.",
           "// ============================================================================",
           "", "data class Strings3("]
    for key in S:
        k = kind(key)
        if k == "text":
            out.append(f"    val {key}: String,")
        elif k == "plural":
            out.append(f"    val {key}: (Int) -> String,")
        else:
            n = len(params(S[key]["en"]))
            out.append(f"    val {key}: ({', '.join(['String'] * n)}) -> String,")
    out.append(")")
    out.append("")
    obj = {"en": "EnglishStrings3", "no": "NorwegianStrings3", "de": "GermanStrings3"}
    for lang in LANGS:
        out.append(f"val {obj[lang]} = Strings3(")
        for key in S:
            v = S[key][lang]
            k = kind(key)
            if k == "text":
                out.append(f"    {key} = {kt_str(v)},")
            elif k == "plural":
                out.append(f"    {key} = {{ n -> if (n == 1) {kt_template(v[0], ['n'])} else {kt_template(v[1], ['n'])} }},")
            else:
                n = len(params(S[key]["en"]))
                args = list(names[:n])
                out.append(f"    {key} = {{ {', '.join(args)} -> {kt_template(v, args)} }},")
        out.append(")")
        out.append("")
    out.append("fun strings3For(language: AppLanguage, systemLanguage: String): Strings3 = when (language.resolve(systemLanguage)) {")
    out.append("    AppLanguage.GERMAN -> GermanStrings3")
    out.append("    AppLanguage.NORWEGIAN -> NorwegianStrings3")
    out.append("    else -> EnglishStrings3")
    out.append("}")
    out.append("")
    (ROOT / "app/src/main/java/no/srrlsm/speechsplit/core/Strings3.kt").write_text("\n".join(out))


if __name__ == "__main__":
    js()
    kt()
    print("Wrote docs/app/strings3.js and core/Strings3.kt")
