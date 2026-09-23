Buildings the settlement can raise.
===================================

One file is one building. Two formats are read, and neither needs another plugin:

  .schem   WorldEdit / Axiom schematics. Read directly - WorldEdit does NOT have to be
           installed. This is the format most downloadable builds come in.

  .nbt     Minecraft structure files, the kind a structure block saves.


FOLDERS
-------
Each folder is a kind of country, and a settlement is only offered the buildings that
suit the land it stands on:

  allbiomes/   offered everywhere
  plains/
  desert/
  savanna/
  taiga/
  snow/
  jungle/
  swamp/

This replaced swapping a building's materials to match the local biome. Substituting
sandstone into something designed in oak produces a building nobody drew - and the more
care went into the original, the worse the result. Sorting by region gets deserts that
look like deserts out of buildings that were each designed for where they end up.


WHERE BUILDINGS COME FROM
-------------------------
Three ways, in rough order of effort:

1. RECORDED FROM A VILLAGE YOU FOUND
   Settlements you visit are recorded automatically: their buildings are measured, saved
   into the folder for that kind of country, and become available to your other villages
   of the same kind. This is the point of it - a datapack's villages have architecture you
   could not otherwise get out of the world and into a menu.

2. DOWNLOADED
   Drop a .schem straight in. Nothing else to do.

3. SAVED BY HAND
   Build it, then:
     /give @s structure_block
   Place it, set it to SAVE mode, name it, size the outline over your building, hit SAVE.
   The file appears in <world>/generated/minecraft/structures/<name>.nbt - copy it here.

Run /rv reload after adding files by hand.


WHAT IS CHARGED FOR
-------------------
The settlement pays for the blocks the building is made of, out of its own stores, which
its workers fill. Doors, beds and tall flowers cost one item rather than two, and anything
that cannot be held as an item - water, for instance - is free.


HOW LONG IT TAKES
-----------------
Worked out from the number of blocks, so a cottage goes up quickly and a keep takes a
while. The sitting mayor's programme speeds it up or slows it down.


TIPS
----
- Start the file at the floor, and keep a block of the ground around the building in it.
  A building goes back at the height its own surroundings say it stood at: ground in the
  outer ring of the bottom layer means it was cut into the earth, and it is laid in place
  of the turf; air and grass tufts there mean it stood on top of the ground, and it is
  laid on top. Crop that ring away and the building is stood on the ground, which is the
  safer of the two guesses.
- Include the foundation. A building is placed exactly as saved, so whatever is under it
  in the file is what gets laid.
- Keep the outline tight. Empty space inside it stays empty, and a loose outline makes the
  building count as bigger than it is - which matters, because buildings are kept from
  being placed too close together.
