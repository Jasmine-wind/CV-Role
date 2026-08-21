// Minimal v1 — built-in read-only template. Presentation only; all content comes from the
// generated data.typ where every user value is an escaped string literal.
#import "data.typ": resume

#let ink = rgb("#1f2329")
#let muted = rgb("#6b7280")
#let rule = rgb("#c9cdd4")

#set page(paper: "a4", margin: (x: 2.2cm, y: 2cm))
#set text(
  font: ("Noto Sans CJK SC", "FandolHei", "Noto Sans", "DejaVu Sans"),
  size: 10pt,
  fill: ink,
  lang: "zh",
)
#set par(leading: 0.65em, justify: true)

#let basics = resume.basics
#let sections = resume.sections

#let contact-item(contact) = {
  if contact.label != "" {
    contact.label + ": " + contact.value
  } else {
    contact.value
  }
}

#let render-header() = {
  if basics.name != "" {
    text(size: 19pt, weight: "semibold", basics.name)
    v(0.3em)
  }
  if basics.contacts.len() > 0 {
    text(size: 9.5pt, fill: muted, basics.contacts.map(contact-item).join("   ·   "))
    v(0.2em)
  }
}

#let section-title(title) = {
  v(0.85em)
  text(size: 10.5pt, weight: "semibold", upper(title))
  v(0.25em)
  line(length: 100%, stroke: 0.4pt + rule)
  v(0.5em)
}

#let render-entry(entry) = {
  if entry.heading != "" or entry.meta != "" {
    grid(
      columns: (1fr, auto),
      align: (left, right),
      text(weight: "medium", entry.heading),
      text(size: 9pt, fill: muted, entry.meta),
    )
    v(0.15em)
  }
  if entry.bullets.len() > 0 {
    list(
      marker: text(fill: muted)[–],
      spacing: 0.35em,
      ..entry.bullets,
    )
  }
  v(0.35em)
}

#render-header()
#for section in sections {
  section-title(section.title)
  for entry in section.entries {
    render-entry(entry)
  }
}
