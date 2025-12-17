# Yeetables

A Minecraft Paper plugin for data-driven throwable items with configurable physics, abilities, and rendering.

Download on Modrinth: [modrinth.com/plugin/yeetables](https://modrinth.com/plugin/yeetables)

## Default Yeetables

- **Popped Chorus Fruit** - Thrower swaps positions with the target
- **Grappling Hook** - What you think it does
- **Brick** - Basic projectile with direct damage. nether and resin bricks also work
- **Slimeball** - Bounces multiple times off surfaces with knockback
- **Bomb** - Explodes on impact (no block damage)
- **Fire Charge** - Launches a fireball
- **Paper Airplane** - Slow glider with low gravity. gives knockback on hit
- **Magma Cream** - Ignites targets
- **Fermented Spider Eye** - Applies weakness effect
- **Clay Ball** - Applies slowness effect

all of the above are configurable and data driven. you could make a brick that explodes, or a slimeball that applies potion effects, whatever you want.

## Features

- Define custom throwable projectiles via YAML configuration. can be a renamed item, custom textured player head, anything.
- Configurable physics (speed, gravity, accuracy, knockback, etc)
- Built-in abilities: bounce, explode, fireball, ignite, potion effects, grapple, swap positions
- Multiple render types: simple items, block displays, item displays
- Custom sounds, particles, and item consumption

## Commands

- `/yeetables reload` - Reload configuration
- `/yeetables list` - List all defined yeetables
- `/yeetables give <item>` - Give yourself a throwable item

## Configuration

Define throwables in `yeetables.yml`. Example:

```yaml
yeetables:
  - id: brick
    item:
      material: BRICK
    properties:
      speed: 1.0
      accuracy-offset: 0.05
      cooldown: 600
      damage: 4.0
    render:
      type: simple
      material: BRICK
    consumption: MAIN_HAND
```