import Foundation
import Vision

// Read the composited iPhone screen, including WebKit's GPU surface.
let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.recognitionLanguages = ["it-IT"]
let handler = VNImageRequestHandler(url: URL(fileURLWithPath: CommandLine.arguments[1]))
try handler.perform([request])
let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string.lowercased() }
print(lines.joined(separator: " | "))
let words = lines.joined(separator: " ")
let action = ["avanti", "salta"].contains(where: words.contains)
let title = ["un unico posto", "che conta"].contains(where: words.contains)
exit(action && title ? 0 : 1)
