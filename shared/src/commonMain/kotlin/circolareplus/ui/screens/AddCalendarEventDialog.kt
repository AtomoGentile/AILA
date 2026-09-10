package circolareplus.ui.screens

// Questo file è superato: la dialog "Nuovo evento di classe" (AddCalendarEventDialog) ora vive
// dentro MainAppShell.kt (usa FlowRow + AnimatedFilterChip per le categorie invece di
// EventCategorySelector). Questo vecchio file, mai rimosso dopo il refactor, dichiarava una
// SECONDA funzione pubblica con lo stesso nome nello stesso package: è la causa reale degli
// errori "Conflicting overloads" / "Cannot infer type for this parameter" del 6/9, non un
// problema di cache o di nomi dei parametri. Svuotato invece di cancellato per sicurezza — se
// tutto compila puoi cancellare tu stesso questo file (o dimmelo e te lo chiedo per bene).
