import DOMPurify from 'dompurify'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import rust from 'highlight.js/lib/languages/rust'

for (const [name, grammar] of Object.entries({ c, cpp, java, python, rust })) hljs.registerLanguage(name, grammar)
export function renderSolutionMarkdown(text = '') {
  return DOMPurify.sanitize(marked.parse(text, { gfm: true, breaks: false }), {
    ALLOWED_TAGS: ['p','br','ul','ol','li','strong','em','code','pre','table','thead','tbody','tr','th','td','blockquote','h3','h4','h5','hr','a'],
    ALLOWED_ATTR: ['href','title'], FORBID_TAGS: ['img','style','script','iframe'],
  })
}
export function renderSolutionSvg(svg = '') {
  return DOMPurify.sanitize(svg, {
    ALLOWED_TAGS: ['svg','g','rect','circle','ellipse','line','polyline','polygon','path','text','tspan','defs','marker','title','desc'],
    ALLOWED_ATTR: ['viewBox','width','height','x','y','x1','y1','x2','y2','cx','cy','r','rx','ry','points','d','fill','stroke','stroke-width','stroke-linecap','stroke-linejoin','stroke-dasharray','opacity','fill-opacity','stroke-opacity','font-size','font-family','font-weight','text-anchor','dominant-baseline','transform','id','xmlns','role','aria-label','dx','dy'],
    FORBID_TAGS: ['style','script','foreignObject','image','use','a','animate','set'],
  }).replace(/\s(?:fill|stroke)="[^"]*(?:url\(|javascript:|data:)[^"]*"/gi, '')
}
export function highlightSolutionCode(code = '', language = 'cpp') {
  return hljs.highlight(code, { language: Object.hasOwn({ c, cpp, java, python, rust }, language) ? language : 'cpp', ignoreIllegals: true }).value
}
