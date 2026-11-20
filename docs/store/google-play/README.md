# Google Play listing graphics

## Ready to upload

- `icon-512.png`: 512 x 512 RGBA PNG, derived from the existing application artwork. Transparency is preserved; no launcher resources were changed.
- `feature-graphic-en-US.png`: 1024 x 500 RGB PNG, approved by the maintainer. Copy: **MinAndroid / Your Mina node. / On your Android device.**

The feature graphic was created with the built-in image generation tool using the existing app logo as a reference, a dark navy background, restrained teal/violet lighting and subtle network lines. The approved 1774 x 887 proposal was fitted to the required store dimensions without adding claims or app UI. The logo in the banner is AI-rendered; the separate store icon is resized from the original artwork.

## Supplied screenshots

`references/node-light-original.jpg` is the supplied 508 x 1280 home screenshot, retained unchanged as a reference. Its aspect ratio is not 9:16; it is not the final phone-upload asset.

The supplied scrolling Monitor image was only 168 x 1280. It was not included as a store screenshot because its text is too small and its width is below the 320-pixel minimum shown by Play Console. Upscaling cannot restore the missing detail.

Provide original, non-scrolling screenshots taken directly on the device, ideally four 1080 x 1920 images (or other dimensions accepted by Play Console). Suggested screens: Node, Monitor resources, production history, and a block detail or dashboard. Do not include passwords, plaintext private keys or personal information. Show actual application behavior; do not invent UI or node statistics.

The original attachment directory stays ignored by Git. Only these selected public assets are tracked.

The three non-scrolling Monitor screenshots in `screenshots/en-US/` are retained as supplied, without resizing or changing UI content. Each measures 576 x 1280 (ratio 9:20), not the 9:16 format requested by the displayed Play Console. They need an approved framing/crop or new captures before uploading to that field; these are not 1080-pixel-wide promotional assets. Use Resources and Production first; Timings/history is an additional view.
