<img src="docs/images/social-preview.png" alt="Trailog - offline maps and GPS tracks">

<h1>Trailog
<img src="app/src/main/assets/flags/gb.svg" alt="English" width="24" align="right">
<a href="docs/README.fr.md"><img src="app/src/main/assets/flags/fr.svg" alt="Français" width="24" align="right"></a>
</h1>

**Offline mapping and routes for Android.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml/badge.svg)](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/lc-4918/trailog)](https://github.com/lc-4918/trailog/releases)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![Ask DeepWiki](https://img.shields.io/badge/Ask-DeepWiki-5A4FCF)](https://deepwiki.com/lc-4918/trailog)

Trailog is a native Android application for viewing, importing and organising GPS tracks
(hiking, cycling, mountain biking, exploring), on customisable basemaps, and built for
offline use.

[//]: # (<table width="100%">)

[//]: # (  <tr>)

[//]: # (    <td colspan="3" align="center"><img src="docs/screenshots/1.jpg" alt="A track on the map" width="330"><br><sub>A track on the map</sub></td>)

[//]: # (  </tr>)

[//]: # (  <tr>)

[//]: # (    <td align="center" width="33.3%"><img src="docs/screenshots/2.jpg" alt="Synchronised elevation profile" width="100%"><br><sub>Elevation profile, synchronised</sub></td>)

[//]: # (    <td align="center" width="33.3%"><img src="docs/screenshots/3.jpg" alt="Marker info bubble with a photo" width="100%"><br><sub>Marker bubble, photo included</sub></td>)

[//]: # (    <td align="center" width="33.3%"><img src="docs/screenshots/4.jpg" alt="Library of folders and layers" width="100%"><br><sub>Library of folders and layers</sub></td>)

[//]: # (  </tr>)

[//]: # (  <tr>)

[//]: # (    <td align="center"><img src="docs/screenshots/5.jpg" alt="Route planner" width="100%"><br><sub>Route planner</sub></td>)

[//]: # (    <td align="center"><img src="docs/screenshots/6.jpg" alt="Basemap manager" width="100%"><br><sub>Basemap manager</sub></td>)

[//]: # (    <td align="center"><img src="docs/screenshots/7.jpg" alt="Settings, Map tab" width="100%"><br><sub>Settings, Map tab</sub></td>)

[//]: # (  </tr>)

[//]: # (</table>)

> The project's working documents (specification, design notes, tests) are written in French.
> This README is their English counterpart.

---

## What is Trailog?

Trailog keeps your tracks organised locally on your phone, and shows them on a map with a
synchronised elevation profile, without depending on any online service.

**Typical uses:**
- Prepare an outing: compute a route or import a track, and check its surfaces and elevation before leaving.
- Download the basemap before the outing, over the whole area or along the route, to use it with no network.
- Follow the route in the field, with an alert as soon as you stray from it.
- Hiking, cycling, mountain biking: follow a route prepared in advance, offline.
- Archive personal tracks, sorted into folders.
- Explore specialised basemaps (national mapping agencies, hillshade, cycle routes...).

## Quick start

1. **Import a track** (*Import* button, GPX, GeoJSON or KML/KMZ), or **compute a route** between two or more stops.
2. **Choose the destination**: an existing folder, or a new one.
3. **View it**: tap the route in the side menu to see it on the map with its elevation profile; tapping either one moves the cursor on the other.
4. **Check the surfaces before heading out**: the details show how much of the route is paved or unpaved, surface by surface (asphalt, gravel, ground, sand...), and the types of ways it follows.
5. **Take the map along**: download the basemap along the route, with no network needed in the field.
6. **Follow it with the off-track alert**: Trailog warns you as soon as you stray from the route.

## Main features

- **Native map** (MapLibre) with many configurable basemaps, including composite ones (a base plus an overlay).
- **Track import** in GPX, GeoJSON and KML/KMZ with instant statistics, also through the phone's "Open with" and "Share to".
- **Elevation profile** synchronised with a cursor on the map, with zoom on a section and an adjustable vertical scale.
- **Surfaces and types of ways** of a track or a route, shown before the outing: paved or not, asphalt, gravel, ground...
- **Folder organisation**: create, rename, move and delete folders and routes, and colour a whole folder at once.
- **Points of interest** on the map: markers with editable info bubbles (title, text, links, photos).
- **Points of interest along the way** (DATAtourisme in France, OpenStreetMap everywhere): lodging, food, services, water, filterable.
- **Offline maps**: download an area or the **corridor along a track**, import your own MBTiles basemaps, take the points of interest along.
- **Place and address search**, the place found being pinned on the map with its address.
- **Route planning** from 2 to 25 stops, for five activities, with an elevation profile, surfaces, and a **recompute on every change**.
- **Numbered stops on the map**: A and B for the ends, numbered dots in between, which you can move by hand or remove.
- **Your own position as a stop**, up to date every time you reopen the planner, or on demand with the *Refresh* button.
- **Start, end or stop in three gestures** from any info bubble, or from a long press anywhere on the map.
- **Long press on the map**: the address of the spot, and the distance and time to reach it from your position or another point.
- **Measuring along a track**: the distance between two points of a displayed track, along the route, with no network.
- **Off-track alert**: pick the track you follow and get a banner, and a sound if you want one, as soon as you stray from it.
- **Dashboard while following**: speed, time and distance done and left, ascent and descent done and ahead.
- **Tracking survives the screen going off**, with an ongoing notification and an option to keep the screen on.
- **Heading arrow** that follows your direction of travel rather than the compass.
- **Send to a Garmin watch**: the corridor of a track, as map tiles, through Garmin Connect.
- **Built-in updates**, and an interface in French, English, German, Spanish, Catalan, Basque, Italian and Portuguese.

## Installation

**Requirements:** Android 7.0 (API 24) or later.

### From GitHub Releases (recommended)

1. Open the repository's [Releases](https://github.com/lc-4918/trailog/releases) page.
2. Download the `.apk` of the latest version. Each release offers several: pick the one matching
   your phone's architecture - `arm64-v8a` for any recent device, `armeabi-v7a` for an older one -
   half the size of the plain `trailog-vX.Y.Z.apk`, which carries them all and works anywhere if in
   doubt.
3. Open the downloaded file on your phone (allow installation from an unknown source if Android
   asks for it).
4. Confirm the installation.

> Trailog is not distributed on the Play Store: GitHub Releases serves as the distribution
> platform. See [Contributing & Development](#contributing--development) for how this "store"
> works.

### Updates

Once that first installation is done, you will not have to come back here: Trailog checks for
itself whether a newer version exists and offers to install it.

- By default, the check happens **at startup**.
- You can switch it to **manual** under **Settings -> System -> Updates**, where a button then lets
  you check whenever you want.
- When you accept, Trailog downloads the new version and starts the installation. Android will ask
  you once for permission to install applications from Trailog: this is normal for an application
  distributed outside a store, and you can withdraw it at any time in the Android settings.
- Your tracks, folders and settings are kept.

## Going further

- **Import/export**: GPX, GeoJSON and KML/KMZ in; GeoJSON out; photos of GPX waypoints are collected.
- **Editing an info bubble**: the pencil changes the title, text, link and photos, or deletes the point.
- **Taking a map offline**: draw an area, or pick a track for its corridor, set the zoom range, and the tiles go into a layer.
- **Basemaps**: manage tile providers (URL, API key) and create composite basemaps in the settings.
- **Local offline basemaps**: import an `.mbtiles` file to use without a connection.
- **Basemap legend**: some basemaps, such as the AF3V cycle routes, unfold their legend from a button on the map.
- **Searching for a place**: enable geocoding in **Settings / Map**; results mix a place's importance and its distance.
- **Querying a point**: a long press off a marker pins the spot, gives its address and two measurements.
- **Distance and time to a point**: those of the recommended route for the activity set in *Settings / Trips* (Photon and Valhalla, or your own instances).
- **Moving a stop**: tap a numbered dot for *Set as destination* or *Delete*, or hold it briefly and drop it elsewhere.
- **Adding a stop beyond the ends**: a new start or end from the map keeps the old one as a stop.
- **Measuring a section of a track**: enable the ruler button in **Settings / Map**, then tap two points on a track.
- **Colour by slope**: off by default, in *Settings / Trips*, for the route on the map and its profile.
- **Hillshade**: enable relief shading in the map settings.
- **Elevation profile**: zoom on a section (three levels), smoothing and vertical scale such as 1 cm = 100 m.
- **Sending to a Garmin watch**: choose the width of the corridor, then start the sync from the watch.
- **Personalisation**: avatar, units, menu opening mode, touch tolerance, info bubble position, text sizes.

## Data & Privacy

- No online tracking, no telemetry, no account.
- All tracks, points and settings are stored **locally** on the device.
- Network requests are limited to loading tiles from the providers you have configured, and to
  checking for updates on GitHub. The latter transmits nothing about you: it reads a public file
  stating the latest published version. You can switch it to manual in the settings.
- **Place search**, **the address of a point** and **distance measurements** are the only functions
  that query a third-party service while you use them. A search sends the text you typed, and
  nothing else - neither your position, nor the area you are looking at. The address of a point
  sends that point, the one you have just picked. A distance measurement sends the two points
  concerned (including your GPS position if you ask for the distance from it) to the routing
  service. Search by name is **disabled by default**, and both services are configurable: you can
  host your own.
- The **points of interest layer**, when you turn it on, asks its two sources - DATAtourisme and
  OpenStreetMap - for the venues in the area you are looking at, and nothing else: not who you are, not
  where you are. It is **off by default**, and asks for nothing more as long as the map stays within what
  has already been loaded.
- **Location tracking** never leaves the device: not recorded, not sent, not kept once tracking stops. It
  runs in a service its notification announces at all times, and that you stop in one tap. Trailog does
  not ask for background location permission: tracking only ever starts from a gesture of yours.
- **Measuring along a track**, on the other hand, never leaves the phone: it is read from the track
  you imported, and queries no service.

## Contributing & Development

Development happens in the open on GitHub. The working documents are in French:

- Full technical guide (setup, architecture, build): see [`DEVELOPER.md`](docs/DEVELOPER.md).
- What the application does, in detail: see [`SPEC.md`](docs/SPEC.md); basemaps and the rules that
  govern them: see [`BASEMAPS.md`](docs/BASEMAPS.md).
- Why it is built this way: see [`CONTEXT.md`](docs/CONTEXT.md); what the tests lock down: see
  [`TESTS.md`](docs/TESTS.md).
- How CI/CD and releases work: see [`WORKFLOW.md`](docs/WORKFLOW.md).
- Report a bug or suggest a feature: [GitHub Issues](https://github.com/lc-4918/trailog/issues).

## Licence

Trailog is distributed under the **GPL v3** licence. See the [`LICENSE`](LICENSE) file.

The routing profiles shipped in `app/src/main/assets/brouter/` are taken verbatim from the
[BRouter](https://github.com/abrensch/brouter) project (MIT licence), which retains their authorship.

## Contact

For any question, open a [discussion or an issue](https://github.com/lc-4918/trailog/issues) on the
GitHub repository.
