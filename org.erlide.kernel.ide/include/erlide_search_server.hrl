%% Description: References used for searches, generated by erlide_noparse
%% Author: jakob (jakobce at g mail dot com)
%% Created: 21 mar 2010

-record(ref, {data, offset, length, function, arity, clause, sub_clause}).

-record(external_call, {module, function, arity}).
-record(local_call, {function, arity}).
-record(function_def, {function, arity}).
-record(include_ref, {filename}).
-record(macro_ref, {macro}).
-record(record_ref, {record}).
-record(macro_def, {macro}).
-record(record_def, {record}).
-record(type_ref, {module, type}).
-record(type_def, {type}).
-record(module_def, {module}).
-record(var_def, {variable}).
-record(var_ref, {variable}).
-record(var_pattern, {vardefref, function, arity, clause}).

%% f�rslag p� hur scanner, parser och search jobbar ihop:
%%
%%
%% Filer        Process                          Modul
%%
%% .scan        <scannernamn>  <- l�ser/skriver  erlide_scanner_server.erl   
%%
%% .noparse     - ingen -     <- l�ser          erlide_noparse.erl   skriver -> .refs
%%
%% .refs  <-l�ser erlide_search_server    erlide_search_server.erl
%%
%% scanner-server talar om f�r search-server n�r buffert st�ngs (listener av ngt slag?)
%% noparse uppdaterar search-server n�r en buffert parsas om
%% noparse skapar .refs-fil om den ska skapa .noparse-fil

%% editorbuffert
%% - alltid i scan
%% - alltid i parse (? ingen process �n, bara modell p� java-sidan)
%% - ev. i search (LRU)
%%
%% .erl-fil ej i editor
%% - aldrig i scan
%% - ev. i parse (LRU ? ingen process �n, ev modell p� java-sidan)
%% - ev. i search (LRU)
%%
%% search h�mtar data
%% - alltid via noparse, som ger {read, Refsfile} eller {refs, [...]} tillbaka

%% cache-filer b�r g�ras om!
%% skapa endast vid read och write
%% h�ll scanner i minnet omm editorbuffert finns
