const fs = require("fs");
const path = require("path");
const sharp = require("sharp");

const projectRoot = path.resolve(__dirname, "..");
const registryPath = path.join(projectRoot, "design", "brand_registry_v1.json");
const registry = JSON.parse(fs.readFileSync(registryPath, "utf8"));
const sourceRoot = registry.assetRoot;
const sourceIndex = JSON.parse(
  fs.readFileSync(path.join(sourceRoot, "index.json"), "utf8").replace(/^\uFEFF/, "")
);
const outputRoot = path.join(projectRoot, "app", "src", "main", "assets", "brand_icons");

fs.mkdirSync(outputRoot, { recursive: true });

function normalizedLabel(id) {
  return id.replace(/[-_.]+/g, " ").replace(/\s+/g, " ").trim();
}

function allBrands() {
  const explicit = new Map(registry.brands.map(brand => [brand.id, { ...brand }]));
  const priorities = { payment: 4, email: 3, banking: 2, technology: 1 };
  const selected = new Map();
  for (const item of sourceIndex) {
    const previous = selected.get(item.brand);
    if (!previous || (priorities[item.category] || 0) > (priorities[previous.category] || 0)) {
      selected.set(item.brand, item);
    }
  }
  for (const [id, item] of selected) {
    if (explicit.has(id)) continue;
    const label = normalizedLabel(id);
    explicit.set(id, {
      id,
      categories: [item.category],
      asset: item.file.replace(/\\/g, "/"),
      domains: [],
      issuerAliases: [id, label],
      nameAliases: [id, label]
    });
  }
  return [...explicit.values()].sort((left, right) => left.id.localeCompare(right.id));
}

async function surfaceMode(image, forceLight) {
  if (forceLight) return "light";
  const { data, info } = await sharp(image).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let weight = 0;
  let luminance = 0;
  for (let offset = 0; offset < data.length; offset += info.channels) {
    const alpha = data[offset + 3] / 255;
    if (alpha < 0.08) continue;
    const value = 0.2126 * data[offset] + 0.7152 * data[offset + 1] + 0.0722 * data[offset + 2];
    luminance += value * alpha;
    weight += alpha;
  }
  const average = weight ? luminance / weight : 128;
  if (average < 82) return "light";
  if (average > 225) return "dark";
  return "none";
}

async function renderBrand(brand, lightSurface) {
  const override = registry.iconOverrides && registry.iconOverrides[brand.id];
  const sourceOverride = registry.sourceOverrides && registry.sourceOverrides[brand.id];
  const source = sourceOverride
    ? sourceOverride
    : override
    ? path.join(registry.iconAssetRoot, override)
    : path.join(sourceRoot, ...brand.asset.split("/"));
  const output = path.join(outputRoot, `${brand.id}.webp`);
  let svgInput = fs.readFileSync(source);
  let svgText = svgInput.toString("utf8");
  const viewBoxOverride = registry.viewBoxOverrides && registry.viewBoxOverrides[brand.id];
  if (viewBoxOverride && /<svg\b[^>]*\bviewBox=/i.test(svgText)) {
    svgText = svgText.replace(
      /(<svg\b[^>]*\bviewBox\s*=\s*)("[^"]*"|'[^']*')/i,
      `$1"${viewBoxOverride}"`
    );
  }
  if (/<svg\b[^>]*\bviewBox=/i.test(svgText)) {
    svgInput = Buffer.from(svgText.replace(/<svg\b([^>]*)>/i, (tag, attributes) => {
      const cleaned = attributes
        .replace(/\swidth\s*=\s*("[^"]*"|'[^']*')/ig, "")
        .replace(/\sheight\s*=\s*("[^"]*"|'[^']*')/ig, "");
      return `<svg${cleaned}>`;
    }));
  }
  const logo = await sharp(svgInput, { density: 192, limitInputPixels: false })
    .trim({ background: { r: 0, g: 0, b: 0, alpha: 0 }, threshold: 8 })
    .resize(74, 74, { fit: "contain", withoutEnlargement: false })
    .png()
    .toBuffer();
  const surface = await surfaceMode(logo, lightSurface.has(brand.id));
  await sharp({
    create: {
      width: 96,
      height: 96,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 }
    }
  })
    .composite([{ input: logo, gravity: "centre" }])
    .webp({ lossless: true, quality: 100 })
    .toFile(output);
  return surface;
}

async function main() {
  const brands = allBrands();
  const lightSurface = new Set(registry.lightSurfaceBrands || []);
  const surfaces = new Map();
  for (const brand of brands) {
    try {
      surfaces.set(brand.id, await renderBrand(brand, lightSurface));
    } catch (error) {
      throw new Error(`Failed to render ${brand.id} from ${brand.asset}: ${error.message}`);
    }
  }
  const runtimeRegistry = {
    version: registry.version,
    brands: brands.map(({ id, categories, domains, issuerAliases, nameAliases }) => ({
      id,
      categories,
      domains: domains || [],
      issuerAliases: issuerAliases || [],
      nameAliases: nameAliases || [],
      surface: surfaces.get(id)
    }))
  };
  fs.writeFileSync(
    path.join(outputRoot, "index.json"),
    JSON.stringify(runtimeRegistry, null, 2),
    "utf8"
  );
  console.log(`Rendered ${brands.length} unique brand icons from ${sourceIndex.length} catalog entries to ${outputRoot}`);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
