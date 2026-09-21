import UIKit
import shared

/**
 * Implementazione del bridge SeatMapPdfShareBridge in Swift.
 *
 * Disegna la mappa posti (stessa griglia di SeatMapScreen: banchi con 2 o 3 nomi, orientati
 * rispetto a "Lavagna & Cattedra") su un'unica pagina PDF con UIGraphicsPDFRenderer — nessuna
 * libreria aggiuntiva, stesso spirito di SeatMapPdfExporter.android.kt (formato A4 a 72dpi,
 * stessi margini, stessa barra blu) — e apre subito lo share sheet di sistema tramite
 * UIActivityViewController.
 *
 * `desks` arriva già "appiattito" da Kotlin (SeatMapPdfExporter.ios.kt): un'etichetta, i nomi da
 * mostrare e la posizione (row/column, da 0) di ogni banco. Il layout e' quello di Android: colonne
 * e righe ricavate dalla posizione massima, celle senza banco lasciate vuote.
 *
 * Gli errori (file non scrivibile, nessun view controller) non si perdono in un `print`: il
 * metodo ritorna il messaggio e Kotlin lo trasforma in eccezione, cosi' l'utente vede
 * "Impossibile generare il PDF" come su Android.
 */
class SeatMapPdfShareBridgeImpl: SeatMapPdfShareBridge {

    // Formato A4 in punti a 72dpi: stessa unità usata dall'implementazione Android.
    private let pageWidth: CGFloat = 595
    private let pageHeight: CGFloat = 842
    private let margin: CGFloat = 36

    // Palette allineata al design system AILA (vedi AppTheme.kt: PrimaryBlue, HeroGradient*,
    // TextDark/Muted, Hairline) e alla stessa costante usata da SeatMapPdfExporter.android.kt.
    private let colorTextDark = UIColor(red: 0x0F / 255.0, green: 0x17 / 255.0, blue: 0x2A / 255.0, alpha: 1)
    private let colorTextMuted = UIColor(red: 0x64 / 255.0, green: 0x74 / 255.0, blue: 0x8B / 255.0, alpha: 1)
    private let colorHairline = UIColor(red: 0xE8 / 255.0, green: 0xED / 255.0, blue: 0xF5 / 255.0, alpha: 1)
    private let colorGradientTop = UIColor(red: 0x1B / 255.0, green: 0x2E / 255.0, blue: 0x7A / 255.0, alpha: 1)
    private let colorGradientBottom = UIColor(red: 0x7B / 255.0, green: 0x4F / 255.0, blue: 0xE3 / 255.0, alpha: 1)
    private let colorPrimaryBlue = UIColor(red: 0x3B / 255.0, green: 0x82 / 255.0, blue: 0xF6 / 255.0, alpha: 1)
    private let colorBadgeTint = UIColor(red: 0xEF / 255.0, green: 0xF3 / 255.0, blue: 0xFF / 255.0, alpha: 1)
    private let colorZebraTint = UIColor(red: 0xF8 / 255.0, green: 0xFA / 255.0, blue: 0xFF / 255.0, alpha: 1)
    private let colorShadow = UIColor.black.withAlphaComponent(0.08)

    /// Disegna un rettangolo arrotondato riempito con un gradiente lineare orizzontale
    /// (`colorGradientTop` → `colorGradientBottom`), usato sia per la barra "Lavagna & Cattedra"
    /// sia per la linea d'accento sotto al titolo.
    private func fillRoundedGradient(_ rect: CGRect, cornerRadius: CGFloat, in ctx: CGContext) {
        let path = UIBezierPath(roundedRect: rect, cornerRadius: cornerRadius)
        ctx.saveGState()
        path.addClip()
        let colors = [colorGradientTop.cgColor, colorGradientBottom.cgColor] as CFArray
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) else {
            ctx.restoreGState()
            colorGradientTop.setFill()
            path.fill()
            return
        }
        ctx.drawLinearGradient(
            gradient,
            start: CGPoint(x: rect.minX, y: rect.midY),
            end: CGPoint(x: rect.maxX, y: rect.midY),
            options: []
        )
        ctx.restoreGState()
    }

    /// Ritorna nil se lo share sheet e' stato presentato, altrimenti il motivo del fallimento.
    func presentSeatMapPdf(className: String, generatedOnLabel: String, desks: [SeatMapPdfDesk]) -> String? {
        let data = renderPdf(desks: desks)

        let fileName = "mappa_posti_\(Int(Date().timeIntervalSince1970)).pdf"
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        do {
            try data.write(to: fileURL)
        } catch {
            print("SeatMapPdfShareBridge: impossibile scrivere il PDF temporaneo: \(error)")
            return "impossibile salvare il file temporaneo (\(error.localizedDescription))"
        }

        return presentShareSheet(for: fileURL)
    }

    // MARK: - Disegno PDF

    private func renderPdf(desks: [SeatMapPdfDesk]) -> Data {
        let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)

        return renderer.pdfData { context in
            context.beginPage()
            let top = drawHeader(in: context.cgContext)
            drawDesks(desks, in: context.cgContext, top: top)
            drawFooter(in: context.cgContext)
        }
    }

    /// Riga di firma in fondo alla pagina, per chiudere il documento in modo un po' più curato.
    private func drawFooter(in ctx: CGContext) {
        let y = pageHeight - margin + 4
        ctx.setStrokeColor(colorHairline.cgColor)
        ctx.setLineWidth(1)
        ctx.move(to: CGPoint(x: margin, y: y))
        ctx.addLine(to: CGPoint(x: pageWidth - margin, y: y))
        ctx.strokePath()

        let footerAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 8),
            .foregroundColor: colorTextMuted
        ]
        let text = "Generato con AILA"
        let size = (text as NSString).size(withAttributes: footerAttributes)
        (text as NSString).draw(
            at: CGPoint(x: pageWidth - margin - size.width, y: y + 4),
            withAttributes: footerAttributes
        )
    }

    /// Titolo + data + barra "Lavagna & Cattedra". Restituisce la coordinata Y da cui continuare a disegnare.
    private func drawHeader(in ctx: CGContext) -> CGFloat {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd/MM/yyyy"
        let dateLabel = dateFormatter.string(from: Date())

        let titleAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 21),
            .foregroundColor: colorTextDark
        ]
        let subtitleAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 10),
            .foregroundColor: colorTextMuted
        ]

        var y = margin + 6
        ("Mappa Posti" as NSString).draw(at: CGPoint(x: margin, y: y), withAttributes: titleAttributes)
        y += 26
        ("Generata il \(dateLabel)" as NSString).draw(at: CGPoint(x: margin, y: y), withAttributes: subtitleAttributes)
        y += 16

        // Sottile linea d'accento sfumata sotto al titolo, per legare la testata al resto del
        // design system senza appesantire (stessa coppia di colori del gradiente hero dell'app).
        let ruleRect = CGRect(x: margin, y: y, width: pageWidth - 2 * margin, height: 2)
        fillRoundedGradient(ruleRect, cornerRadius: 0, in: ctx)
        y += 18

        // Barra "Lavagna & Cattedra": stesso gradiente blu-indaco dell'header dell'app, con
        // un'ombra morbida sotto per staccarla dalla pagina invece del riempimento piatto di prima.
        let boardHeight: CGFloat = 26
        let boardRect = CGRect(x: margin, y: y, width: pageWidth - 2 * margin, height: boardHeight)

        let shadowRect = boardRect.offsetBy(dx: 0, dy: 2)
        colorShadow.setFill()
        UIBezierPath(roundedRect: shadowRect, cornerRadius: 8).fill()

        fillRoundedGradient(boardRect, cornerRadius: 8, in: ctx)

        let boardText = "LAVAGNA & CATTEDRA"
        let boardTextAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 11),
            .foregroundColor: UIColor.white,
            .kern: 0.6
        ]
        let boardTextSize = (boardText as NSString).size(withAttributes: boardTextAttributes)
        let boardTextPoint = CGPoint(
            x: boardRect.midX - boardTextSize.width / 2,
            y: boardRect.midY - boardTextSize.height / 2
        )
        (boardText as NSString).draw(at: boardTextPoint, withAttributes: boardTextAttributes)

        y += boardHeight + 20
        return y
    }

    private func drawDesks(_ desks: [SeatMapPdfDesk], in ctx: CGContext, top: CGFloat) {
        guard !desks.isEmpty else { return }

        // Come su Android: la griglia e' larga quanto la colonna piu' a destra + 1 e alta quanto la
        // fila piu' in fondo + 1, e ogni banco va nella sua cella (row/column reali, non l'indice
        // nella lista), lasciando vuote le celle senza banco.
        let columns = max((desks.map { Int($0.column) }.max() ?? 0) + 1, 1)
        let rows = max((desks.map { Int($0.row) }.max() ?? 0) + 1, 1)

        let gutter: CGFloat = 8
        let gridWidth = pageWidth - 2 * margin
        let deskWidth = (gridWidth - gutter * CGFloat(columns - 1)) / CGFloat(columns)

        // L'altezza del banco si adatta allo spazio verticale rimasto sulla pagina, come
        // nell'implementazione Android: con poche righe resta comoda da leggere, con una classe
        // numerosa si stringe quanto basta a restare su un'unica pagina.
        let availableHeight = pageHeight - top - margin
        let rawHeight = (availableHeight - gutter * CGFloat(max(rows - 1, 0))) / CGFloat(rows)
        let deskHeight = min(max(rawHeight, 26), 54)

        let labelAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 6.5),
            .foregroundColor: colorPrimaryBlue
        ]
        let nameFontSize = min(max(deskHeight / 5.2, 7), 9.5)
        let nameAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: nameFontSize),
            .foregroundColor: colorTextDark
        ]

        for desk in desks {
            let row = max(Int(desk.row), 0)
            let col = max(Int(desk.column), 0)
            let left = margin + CGFloat(col) * (deskWidth + gutter)
            let boxTop = top + CGFloat(row) * (deskHeight + gutter)
            let rect = CGRect(x: left, y: boxTop, width: deskWidth, height: deskHeight)

            // Ombra morbida sotto la card, per staccarla dalla pagina invece di un bordo secco.
            let shadowRect = rect.offsetBy(dx: 0, dy: 1.5)
            colorShadow.setFill()
            UIBezierPath(roundedRect: shadowRect, cornerRadius: 6).fill()

            let boxPath = UIBezierPath(roundedRect: rect, cornerRadius: 6)
            (row % 2 == 0 ? UIColor.white : colorZebraTint).setFill()
            boxPath.fill()
            colorHairline.setStroke()
            boxPath.lineWidth = 1
            boxPath.stroke()

            // Etichetta di posizione come piccola pill in alto, invece del testo grigio nudo:
            // più leggibile e coerente con i badge del resto dell'app.
            let labelSize = (desk.label as NSString).size(withAttributes: labelAttributes)
            let badgeWidth = min(labelSize.width + 8, rect.width - 6)
            let badgeRect = CGRect(
                x: rect.midX - badgeWidth / 2, y: rect.minY + 3,
                width: badgeWidth, height: 8
            )
            colorBadgeTint.setFill()
            UIBezierPath(roundedRect: badgeRect, cornerRadius: 4).fill()
            drawCentered(desk.label, in: rect, atY: badgeRect.maxY - 1, attributes: labelAttributes)

            // Blocco nomi centrato verticalmente nello spazio rimasto sotto il badge, non più
            // incollato in alto: con un solo nome in un banco alto restava scentrato e "vuoto".
            let lineHeight = nameFontSize + 3
            let namesHeight = lineHeight * CGFloat(desk.names.count)
            var nameY = max(
                (badgeRect.maxY + rect.maxY - namesHeight) / 2 + nameFontSize,
                badgeRect.maxY + nameFontSize
            )
            for name in desk.names {
                if nameY > rect.maxY - 2 { break }
                let truncated = truncate(name, maxLength: 22)
                drawCentered(truncated, in: rect, atY: nameY, attributes: nameAttributes)
                nameY += lineHeight
            }
        }
    }

    private func drawCentered(_ text: String, in rect: CGRect, atY y: CGFloat, attributes: [NSAttributedString.Key: Any]) {
        let size = (text as NSString).size(withAttributes: attributes)
        let point = CGPoint(x: rect.midX - size.width / 2, y: y - size.height)
        (text as NSString).draw(at: point, withAttributes: attributes)
    }

    private func truncate(_ text: String, maxLength: Int) -> String {
        guard text.count > maxLength else { return text }
        return String(text.prefix(maxLength - 1)) + "…"
    }

    // MARK: - Share sheet

    /// Ritorna nil se la presentazione e' stata avviata, altrimenti il motivo del fallimento.
    private func presentShareSheet(for fileURL: URL) -> String? {
        guard let rootViewController = topMostViewController() else {
            print("SeatMapPdfShareBridge: nessun view controller su cui presentare lo share sheet.")
            return "nessuna schermata su cui aprire la condivisione"
        }

        let activityVC = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)

        // Su iPad UIActivityViewController richiede un popoverPresentationController, altrimenti
        // va in crash: lo impostiamo in modo difensivo, senza effetto su iPhone.
        activityVC.popoverPresentationController?.sourceView = rootViewController.view
        activityVC.popoverPresentationController?.sourceRect = CGRect(
            x: rootViewController.view.bounds.midX,
            y: rootViewController.view.bounds.midY,
            width: 0,
            height: 0
        )

        DispatchQueue.main.async {
            rootViewController.present(activityVC, animated: true)
        }
        return nil
    }

    /// Trova il view controller "più in alto" attualmente presentato, attraversando le scene
    /// connesse fino alla finestra chiave e poi la catena di presentedViewController.
    private func topMostViewController() -> UIViewController? {
        let keyWindow = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }

        guard var topController = keyWindow?.rootViewController else {
            return nil
        }

        while let presented = topController.presentedViewController {
            topController = presented
        }

        return topController
    }
}
