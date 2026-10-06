/**
 * 页面导出 PDF（需求 BI-DSH-R04，前端截图方案）：把元素渲染为图片（html-to-image，内联计算样式，SVG 图表与主题色可正确输出），
 * 按 A4 横向分页写入 PDF（jsPDF）。两个库按需加载，不进入首屏包。
 */
export async function exportElementPdf(el: HTMLElement, filename: string): Promise<void> {
  const [{ toCanvas }, { jsPDF }] = await Promise.all([import('html-to-image'), import('jspdf')])
  const background = getComputedStyle(document.body).backgroundColor
  const canvas = await toCanvas(el, {
    pixelRatio: 2,
    backgroundColor: background,
    cacheBust: true,
    filter: (node) => !(node instanceof HTMLElement && node.dataset.pdfIgnore !== undefined)
  })
  const pdf = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4', compress: true })
  const margin = 8
  const pageW = pdf.internal.pageSize.getWidth() - margin * 2
  const pageH = pdf.internal.pageSize.getHeight() - margin * 2
  // 每页对应的原图像素高度（按宽度等比缩放）
  const slicePx = Math.floor((pageH * canvas.width) / pageW)
  const slice = document.createElement('canvas')
  slice.width = canvas.width
  for (let y = 0, page = 0; y < canvas.height; y += slicePx, page++) {
    const h = Math.min(slicePx, canvas.height - y)
    slice.height = h
    const ctx = slice.getContext('2d')
    if (!ctx) break
    ctx.fillStyle = background
    ctx.fillRect(0, 0, slice.width, h)
    ctx.drawImage(canvas, 0, y, canvas.width, h, 0, 0, canvas.width, h)
    if (page > 0) pdf.addPage()
    pdf.addImage(slice.toDataURL('image/jpeg', 0.92), 'JPEG', margin, margin, pageW, (h * pageW) / canvas.width)
  }
  // 自行触发下载（jsPDF.save 在部分浏览器下会丢失文件名）
  const url = URL.createObjectURL(pdf.output('blob'))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
