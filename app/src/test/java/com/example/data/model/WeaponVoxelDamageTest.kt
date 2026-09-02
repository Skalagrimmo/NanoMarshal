package com.example.data.model

import com.example.engine.DestructibleVoxelBlock
import com.example.engine.Voxel3DCell
import org.junit.Assert.*
import org.junit.Test

class WeaponVoxelDamageTest {

    @Test
    fun testCalculateDamageBasedOnProjectileTypeAndVoxelMaterial() {
        val kineticPistol = Weapon(
            name = "Kinetic Pistol",
            damage = 40,
            projectileType = ProjectileType.BULLET_KINETIC
        )

        val plasmaRifle = Weapon(
            name = "Plasma Carbine",
            damage = 40,
            projectileType = ProjectileType.PLASMA_BOLT
        )

        val rocketLauncher = Weapon(
            name = "Rocket Launcher",
            damage = 40,
            projectileType = ProjectileType.EXPLOSIVE_ROCKET
        )

        val railgun = Weapon(
            name = "Heavy Railgun",
            damage = 40,
            projectileType = ProjectileType.RAILGUN_SLUG
        )

        val concreteWall = VoxelTile(
            gridX = 0,
            gridY = 0,
            type = VoxelType.CONCRETE_WALL,
            health = 200f,
            durability = 150f
        )

        val alienBiomass = VoxelTile(
            gridX = 1,
            gridY = 0,
            type = VoxelType.ALIEN_BIOMASS,
            health = 200f,
            durability = 50f
        )

        val reinforcedMetal = VoxelTile(
            gridX = 2,
            gridY = 0,
            type = VoxelType.REINFORCED_METAL,
            health = 300f,
            durability = 250f
        )

        // Kinetic weapon vs concrete gets structural bonus
        val kineticVsConcrete = kineticPistol.calculateDamage(concreteWall)
        assertTrue("Kinetic vs concrete should deal significant damage", kineticVsConcrete > 40f)

        // Plasma vs alien biomass gets high thermal bonus
        val plasmaVsBiomass = plasmaRifle.calculateDamage(alienBiomass)
        val kineticVsBiomass = kineticPistol.calculateDamage(alienBiomass)
        assertTrue("Plasma should deal higher damage to alien biomass than kinetic", plasmaVsBiomass > kineticVsBiomass)

        // Explosive vs concrete wall deals massive structural multiplier
        val explosiveVsConcrete = rocketLauncher.calculateDamage(concreteWall)
        assertTrue("Explosive projectile should deal higher structural damage than kinetic", explosiveVsConcrete > kineticVsConcrete)

        // Railgun vs reinforced metal deals high penetration damage
        val railgunVsMetal = railgun.calculateDamage(reinforcedMetal)
        val kineticVsMetal = kineticPistol.calculateDamage(reinforcedMetal)
        assertTrue("Railgun slug should penetrate metal far more effectively than kinetic rounds", railgunVsMetal > kineticVsMetal)
    }

    @Test
    fun testUpdateHealthAndDurabilityWithMitigationAndPenetration() {
        val weapon = Weapon(
            name = "Assault Rifle",
            damage = 50,
            projectileType = ProjectileType.BULLET_KINETIC
        )

        val voxel = VoxelTile(
            gridX = 3,
            gridY = 4,
            type = VoxelType.CONCRETE_WALL,
            health = 150f,
            durability = 100f
        )

        val result = weapon.damageVoxel(voxel)

        // Both durability and health must be updated and reduced
        assertTrue("Durability must decrease after damage", voxel.durability < 100f)
        assertTrue("Health must decrease after damage", voxel.health < 150f)
        assertEquals(voxel.health, result.remainingHealth, 0.001f)
        assertEquals(voxel.durability, result.remainingDurability, 0.001f)
        assertFalse("Voxel should still be alive after non-lethal hit", result.wasDestroyed)
        assertFalse(voxel.isDestroyed)
    }

    @Test
    fun testVoxelDestructionAndVisualEffectsTriggerWhenHealthReachesZero() {
        val rocketWeapon = Weapon(
            name = "Thermal Launcher",
            damage = 250,
            projectileType = ProjectileType.EXPLOSIVE_ROCKET
        )

        val fragileVoxel = VoxelTile(
            gridX = 1,
            gridY = 1,
            type = VoxelType.LOW_COVER_CRATE,
            health = 50f,
            durability = 20f
        )

        var destructionCallbackInvoked = false
        var capturedVoxel: DestructibleVoxel? = null
        var capturedEffects: List<Particle>? = null

        val result = rocketWeapon.damageVoxel(
            voxel = fragileVoxel,
            onDestruction = { destroyedVoxel, effects ->
                destructionCallbackInvoked = true
                capturedVoxel = destroyedVoxel
                capturedEffects = effects
            }
        )

        // Health reached zero and destruction triggered
        assertEquals(0f, fragileVoxel.health, 0.001f)
        assertTrue("Voxel must be marked destroyed", result.wasDestroyed)
        assertTrue("VoxelTile must be destroyed", fragileVoxel.isDestroyed)
        assertTrue("VoxelTile should be disintegrated", fragileVoxel.isDisintegrated)
        assertEquals(CoverHeight.NONE, fragileVoxel.coverHeight)

        // Destruction visual effects must be generated
        assertTrue("Destruction callback must be invoked", destructionCallbackInvoked)
        assertNotNull(capturedEffects)
        assertFalse("Destruction visual effects list must not be empty", result.destructionEffects.isEmpty())
        assertTrue("Result destruction effects must match captured effects", result.destructionEffects.size >= 10)

        // Verify particle types include debris and explosive flame effects
        val hasDebrisParticles = result.destructionEffects.any { it.type == ParticleType.DEBRIS_VOXEL }
        val hasExplosionFlames = result.destructionEffects.any { it.type == ParticleType.EXPLOSION_FLAME }
        val hasHitIndicator = result.destructionEffects.any { it.type == ParticleType.HIT_NUMBER && it.text == "DESTROYED" }

        assertTrue("Effects must include voxel debris particles", hasDebrisParticles)
        assertTrue("Effects must include explosion flames for explosive rockets", hasExplosionFlames)
        assertTrue("Effects must include floating DESTROYED indicator text", hasHitIndicator)
    }

    @Test
    fun testDestructionVisualEffectsAcrossDifferentProjectileTypes() {
        val plasmaWeapon = Weapon(
            name = "Plasma Rifle",
            damage = 300,
            projectileType = ProjectileType.PLASMA_BOLT
        )

        val railgunWeapon = Weapon(
            name = "EM Railgun",
            damage = 300,
            projectileType = ProjectileType.RAILGUN_SLUG
        )

        val acidWeapon = Weapon(
            name = "Acid Sprayer",
            damage = 300,
            projectileType = ProjectileType.CORROSIVE_ACID
        )

        val cryoWeapon = Weapon(
            name = "Cryo Cannon",
            damage = 300,
            projectileType = ProjectileType.CRYO_FLECHETTE
        )

        // 1. Plasma destruction effects
        val plasmaTarget = VoxelObject(health = 10f, durability = 10f)
        val plasmaResult = plasmaWeapon.damageVoxel(plasmaTarget)
        assertTrue(plasmaResult.wasDestroyed)
        assertTrue("Plasma destruction should generate plasma sparks", plasmaResult.destructionEffects.any { it.type == ParticleType.PLASMA_SPARK })

        // 2. Railgun destruction effects
        val railgunTarget = VoxelObject(health = 10f, durability = 10f)
        val railgunResult = railgunWeapon.damageVoxel(railgunTarget)
        assertTrue(railgunResult.wasDestroyed)
        assertTrue("Railgun destruction should generate electric bolts", railgunResult.destructionEffects.any { it.type == ParticleType.ELECTRIC_BOLT })

        // 3. Acid destruction effects
        val acidTarget = VoxelObject(health = 10f, durability = 10f)
        val acidResult = acidWeapon.damageVoxel(acidTarget)
        assertTrue(acidResult.wasDestroyed)
        assertTrue("Acid destruction should generate nanite spores", acidResult.destructionEffects.any { it.type == ParticleType.NANITE_SPORE })

        // 4. Cryo destruction effects
        val cryoTarget = VoxelObject(health = 10f, durability = 10f)
        val cryoResult = cryoWeapon.damageVoxel(cryoTarget)
        assertTrue(cryoResult.wasDestroyed)
        assertTrue("Cryo destruction should generate cryo crystals", cryoResult.destructionEffects.any { it.type == ParticleType.CRYO_CRYSTAL })
    }

    @Test
    fun testMultipleVoxelImplementationsSupported() {
        val weapon = Weapon(
            name = "Heavy Blaster",
            damage = 150,
            projectileType = ProjectileType.PLASMA_BOLT
        )

        // 1. VoxelTile
        val tile = VoxelTile(gridX = 0, gridY = 0, type = VoxelType.CONCRETE_WALL, health = 80f, durability = 40f)
        val tileResult = weapon.damageVoxel(tile)
        assertTrue("Tile should be destroyed", tileResult.wasDestroyed)

        // 2. DestructibleVoxelBlock
        val block = DestructibleVoxelBlock(x = 0, y = 0, z = 1, type = VoxelType.CONCRETE_WALL, currentDurability = 40f, maxDurability = 40f)
        val blockResult = weapon.damageVoxel(block)
        assertTrue("DestructibleVoxelBlock should be destroyed", blockResult.wasDestroyed)

        // 3. Voxel3DCell
        val cell = Voxel3DCell(x = 0, y = 0, z = 1, type = VoxelType.CONCRETE_WALL, hp = 60f, maxHp = 60f)
        val cellResult = weapon.damageVoxel(cell)
        assertTrue("Voxel3DCell should be destroyed", cellResult.wasDestroyed)

        // 4. VoxelObject
        val obj = VoxelObject(type = VoxelType.LOW_COVER_CRATE, health = 50f, durability = 30f)
        val objResult = weapon.damageVoxel(obj)
        assertTrue("VoxelObject should be destroyed", objResult.wasDestroyed)
    }

    @Test
    fun testIndestructibleVoxelIgnoresDamage() {
        val weapon = Weapon(damage = 200, projectileType = ProjectileType.EXPLOSIVE_ROCKET)
        val indestructibleTile = VoxelTile(
            gridX = 0,
            gridY = 0,
            type = VoxelType.FLOOR_PLAZA,
            health = 100f,
            durability = 100f,
            isDestructible = false
        )

        val result = weapon.damageVoxel(indestructibleTile)
        assertEquals(0f, result.damageDealt, 0.001f)
        assertEquals(100f, indestructibleTile.health, 0.001f)
        assertEquals(100f, indestructibleTile.durability, 0.001f)
        assertFalse(result.wasDestroyed)
        assertTrue(result.destructionEffects.isEmpty())
    }
}
