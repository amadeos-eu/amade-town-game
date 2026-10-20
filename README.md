# amade-town-game

**amade-town-game** is the city builder of the [amadeOS](https://github.com/datazuul/amadeos) desktop, seen in an isometric view: a highway, a river and green land. You lay roads from the highway, zone land for homes, shops and industry, and bring power and water; the town then grows by itself, lot by lot, where people want to live and work. Homes need jobs and shops, shops need people, industry starts a town but its smoke lowers the land value around it. Coal plants and wind turbines make the power, a pump house on the shore and water towers bring the water, and schools, a library, a clinic, a fire station, the town hall and parks make people happier and the land worth more. Taxes pay for the upkeep. Three missions; the maps, the buildings, the missions and the computer's plan are text files. A town can be saved and loaded. Built on [amade-game-engine](https://github.com/datazuul/amade-game-engine); runs on other operating systems as well.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Playing

- A click, **Space** or **Enter** leads from the title to the choice of a mission. Click one, or choose with **left** and **right** and play with **Space** or **Enter**. A mission opens when the one before it is won; the fastest win of each is kept.
- **The goal** of each mission is a number of people, sometimes with a share of them happy and a number of years. The bar at the top shows the money and last month's balance, the people, power and water (used of made), how happy the town is, the date and the speed.
- **The panel** on the right has the minimap, nine tools (also the keys **1** to **9**), the demand for homes, shops and industry, and a box that explains the tool or the tile under the mouse.
- **Road**, **Homes**, **Shops**, **Industry**, the power line and **Bulldoze** are dragged across the map: a road or line from tile to tile, a zone or the bulldozer as a rectangle. The size and the cost follow the mouse. A road over water is a bridge and must run straight.
- **Power**, **Water**, **Services** and **Park** build things, clicked onto the map with their middle under the mouse: green where they fit, red where not. Pressing the key again, or a click on the list in the panel, picks the next thing the tool builds.
- **A lot grows** when its zone is wanted, a road within two tiles leads to the highway, and it has power; from the second level it needs water too, and homes only grow tall near a school. Zones, buildings and lines carry the power to each other; a strip between two blocks needs a line.
- **O** or *Overlay* shows power, water, land value and pollution; **B** or *Budget* shows taxes, upkeep and the tax rate, which **-** and **+** in the box change.
- **The wheel** or **Z** draws the map close up (as the game begins) or shows the whole district.
- **+** and **-** make time run two or four times as fast, and back.
- **F5** saves the town, **F9** loads it, here and on the choice of a mission.
- **Arrow keys**, dragging with the right or middle mouse button, the mouse at the edge of the screen or a click on the minimap move the view. The right button cancels a drag, then the tool.
- **P** or *Menu* pauses, and so does leaving the window; there you can save, load or leave the mission. **Esc** steps back, or pauses when nothing is chosen. After a won mission, **C** keeps building the same town.
- **F11** switches to full screen and back. *Game → Graphics* chooses how the town reaches the screen: **Direct** (at the screen's full size) or **Pixels** (at 640 × 400, enlarged by whole factors). **F12** shows how long each frame takes.
- Sound effects and music can be switched off under *Game → Sound*.

Texts in English, Deutsch, Français, Español and Italiano.

## Files

| What | Where |
|---|---|
| Settings (graphics, sound) | `~/.config/amadeos/town/town.properties` |
| Progress (open missions, fastest wins) | `~/.local/share/amadeos/games/town/progress.save` |
| The saved town (F5) | `~/.local/share/amadeos/games/town/town.save` |

Both save files are written to a new file that then replaces the old one, with the old one kept as `.bak` and a checksum in the last line, so a crash while saving or a damaged file loses nothing. The saved town holds the whole game: every tile with its road, line, zone and lot, the buildings, the fires, the money and the random numbers, so a loaded town goes on exactly as it would have.

## Making missions

The maps are `src/main/resources/eu/amadeos/games/town/maps/map1.txt` to `map3.txt`, one letter per tile: `.` meadow, `T` forest, `~` water and `=` the highway, which must reach the edge of the map, since that is where the people come from. Lines starting with `#` are comments; all rows are equally long, and a map is 8 × 8 tiles at least. A wrong letter or a short row is reported with its file, row and column.

What the town builds and how the computer plays is in `src/main/resources/eu/amadeos/games/town/data/`, explained at the top of each file:

- `buildings.txt`: each building's button, work, size, cost, upkeep, the power it makes or uses, the water, its reach and its smoke, and whether it must stand at the water or exists only once.
- `missions.txt`: each mission's map, the money at the start, the goal in people, how happy they must be, the years, whether houses catch fire, and the buildings it forbids.
- `plan.txt`: the order in which the computer builds its town on the title screen and in the tests.

Change a number, start the game again, play. `GameTest` lets the computer build mission 1 until it is won, so a balance that stalls fails the build.

## Building

```bash
./gradlew test          # the window test needs a display (xvfb-run on servers)
./gradlew run
./gradlew deb           # build/distributions/amade-town-game_<version>_all.deb
./gradlew installToAmadeOs   # copies the package to ../amadeos/config/packages.chroot/
```

All pictures are drawn by the game from vector shapes (`Art`, `Lots`, `Structures`, `Pictures`) at the size of the screen: four looks for each of the three levels of homes, shops and industry, the plants, the water works and the services, trees, cars, buses, lorries and the fire truck, and the ground, computed pixel by pixel with seamless noise. The ground and the shadows on it are kept in one picture of the view that is only painted again where the view moved or the town changed. `./gradlew art` draws the icon from the same shapes, `./gradlew screenshots -Pshots=<folder>` the handbook's screenshots, and `./gradlew benchmark` measures the frame times at both zooms.
