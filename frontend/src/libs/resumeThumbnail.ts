/**
 * 生成简历文件缩略图（PDF 首页 / 图片 / 文本预览）
 */

export async function createResumeThumbnail(file: File): Promise<string | null> {
  const name = file.name.toLowerCase();
  try {
    if (file.type === "application/pdf" || name.endsWith(".pdf")) {
      return await renderPdfThumbnail(file);
    }
    if (file.type.startsWith("image/") || /\.(png|jpe?g|webp|gif)$/i.test(name)) {
      return await fileToDataUrl(file);
    }
    if (/\.(txt|md|csv)$/i.test(name) || file.type.startsWith("text/")) {
      const text = await file.text();
      return renderTextThumbnail(text, file.name);
    }
    if (name.endsWith(".docx")) {
      return renderDocPlaceholder(file.name, "DOCX");
    }
    return renderDocPlaceholder(file.name, "FILE");
  } catch {
    return renderDocPlaceholder(file.name, guessExt(file.name));
  }
}

async function renderPdfThumbnail(file: File): Promise<string> {
  const pdfjs = await import("pdfjs-dist");
  // 使用 CDN worker，避免 Next 打包 worker 路径问题
  pdfjs.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

  const data = new Uint8Array(await file.arrayBuffer());
  const pdf = await pdfjs.getDocument({ data }).promise;
  const page = await pdf.getPage(1);
  const viewport = page.getViewport({ scale: 1 });
  const targetWidth = 180;
  const scale = targetWidth / viewport.width;
  const scaled = page.getViewport({ scale });

  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(scaled.width);
  canvas.height = Math.ceil(scaled.height);
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("canvas unavailable");
  }
  await page.render({
    canvasContext: ctx,
    viewport: scaled,
    canvas,
  }).promise;
  return canvas.toDataURL("image/jpeg", 0.82);
}

function renderTextThumbnail(text: string, fileName: string): string {
  const canvas = document.createElement("canvas");
  const w = 180;
  const h = 240;
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d")!;
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = "#e8e8e8";
  ctx.strokeRect(0.5, 0.5, w - 1, h - 1);

  ctx.fillStyle = "#f5c518";
  ctx.fillRect(0, 0, w, 28);
  ctx.fillStyle = "#333";
  ctx.font = "bold 11px sans-serif";
  ctx.fillText(trimName(fileName, 18), 8, 18);

  ctx.fillStyle = "#555";
  ctx.font = "10px sans-serif";
  const lines = wrapText(text.replace(/\s+/g, " ").trim(), 26).slice(0, 16);
  let y = 46;
  for (const line of lines) {
    ctx.fillText(line, 10, y);
    y += 12;
  }
  return canvas.toDataURL("image/jpeg", 0.85);
}

function renderDocPlaceholder(fileName: string, ext: string): string {
  const canvas = document.createElement("canvas");
  const w = 180;
  const h = 240;
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d")!;

  const grd = ctx.createLinearGradient(0, 0, 0, h);
  grd.addColorStop(0, "#fffdf3");
  grd.addColorStop(1, "#ffe9a8");
  ctx.fillStyle = grd;
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = "#f0d56a";
  ctx.strokeRect(0.5, 0.5, w - 1, h - 1);

  ctx.fillStyle = "#fff";
  ctx.fillRect(24, 36, w - 48, h - 72);
  ctx.strokeStyle = "#e6d39a";
  ctx.strokeRect(24.5, 36.5, w - 49, h - 73);

  ctx.fillStyle = "#c9a227";
  ctx.font = "bold 16px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(ext.toUpperCase(), w / 2, 120);
  ctx.fillStyle = "#666";
  ctx.font = "11px sans-serif";
  ctx.fillText(trimName(fileName, 16), w / 2, 148);
  ctx.textAlign = "left";
  return canvas.toDataURL("image/jpeg", 0.85);
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function wrapText(text: string, maxChars: number): string[] {
  const lines: string[] = [];
  let rest = text;
  while (rest.length > 0 && lines.length < 20) {
    lines.push(rest.slice(0, maxChars));
    rest = rest.slice(maxChars);
  }
  return lines;
}

function trimName(name: string, max: number) {
  const base = name.replace(/\.[^.]+$/, "");
  return base.length > max ? `${base.slice(0, max - 1)}…` : base;
}

function guessExt(name: string) {
  const m = name.match(/\.([^.]+)$/);
  return m ? m[1].toUpperCase() : "FILE";
}

export function formatResumeDate(date = new Date()) {
  const y = date.getFullYear();
  const m = date.getMonth() + 1;
  const d = date.getDate();
  return `${y}/${m}/${d}`;
}
