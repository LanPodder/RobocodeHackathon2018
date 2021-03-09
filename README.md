# RobocodeHackathon2018
Behaviourcode that is run on a virtual Tank to kill other virtual tanks using the Robocode API. \
The format was a All out brawl, where all participating Robots were thrown into a Arena and fought each other until one Robot was left. \
This is what a brawl would look like (Screenshot not from actual hackathon): 

![Robocode Brawl](https://i.imgur.com/8WNWCtW.png) 

## Strategy
### Use two States with following behaviours:

#### While more than 1 enemy is alive:

Find all robot positions and calculate a "force" based on distance to each robot, that gets projected into a steering direction in order to move away from all hostiles.
Shoot the most priority Target based on distance and health.

#### While only 1 enemy Left:

Predict enemy movement direction based on a histogram of "hits" per "situation" (relative time vs movement direction and speed)
Dodge enemy shots using [Wave Surfing](https://robowiki.net/wiki/Wave_Surfing_Tutorial).

