# Google Play listing graphics

## Ready to upload

| Play Console field | File | Dimensions |
| --- | --- | --- |
| Application icon | `icon-512.png` | 512 x 512, RGBA PNG |
| Feature graphic | `feature-graphic-en-US.png` | 1024 x 500, RGB PNG |
| Phone screenshot 1 | `phone/en-US/01-node.png` | 720 x 1280, RGB PNG |
| Phone screenshot 2 | `phone/en-US/02-resources.png` | 720 x 1280, RGB PNG |
| Phone screenshot 3 | `phone/en-US/03-production.png` | 720 x 1280, RGB PNG |
| Phone screenshot 4 | `phone/en-US/04-timings-history.png` | 720 x 1280, RGB PNG |

Each phone image has a 9:16 aspect ratio and is under 8 MiB. Source screenshots were preserved at their original pixel size and centered on matching background side margins, with maintainer approval. No UI content was cropped, enlarged, generated or modified. The screenshots are not 1080-pixel-wide promotional assets; obtaining original higher-resolution captures would improve their clarity further.

## Provenance

The store icon is derived from `android/app/src/main/res/drawable-nodpi/ic_launcher_art.png`, preserving transparency. No launcher resources were changed.

The feature graphic was approved by the maintainer. It was created with the built-in image generation tool using the existing app logo as a reference, a dark navy background, restrained teal/violet lighting and subtle network lines. Copy: **MinAndroid / Your Mina node. / On your Android device.** The approved 1774 x 887 proposal was fitted to the required store dimensions. The logo in the banner is AI-rendered; the separate store icon is resized from the original artwork.

Unmodified supplied originals are retained in `references/` (home, 508 x 1280) and `screenshots/en-US/` (three Monitor views, each 576 x 1280). Upload the formatted files in `phone/en-US/`, not these source references.

The first scrolling Monitor attachment was only 168 x 1280 and was excluded because its text was unreadable. The `.codex-attachments` directory remains ignored; only selected public assets are tracked.
