package cleanui.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MayorTest {
	@Test
	void parsesMayorAndMinister() {
		var m = Mayor.parse("""
				{"success":true,"mayor":{"key":"slayer","name":"Aatrox","perks":[
				 {"name":"SLASHED Pricing","description":"Starting slayer quests is §ehalf price§7."},
				 {"name":"Pathfinder","description":"Gain §b+20 Magic Find§7."}],
				 "minister":{"key":"economist","name":"Diaz","perk":{"name":"Stock Exchange","description":"Stonks.","minister":true}}}}""");
		assertEquals("Aatrox", m.name());
		assertEquals(2, m.perks().size());
		assertEquals("Pathfinder", m.perks().get(1).name());
		assertEquals("Diaz", m.minister());
		assertEquals("Stock Exchange", m.ministerPerk().name());
	}

	@Test
	void readsNeuSkinTexture() {
		String json = "{\"nbttag\": \"{SkullOwner:{Properties:{textures:[0:{Name:\\\"textures\\\",Value:\\\"e3RleHR1cmVz=\\\"}]}}}\"}";
		assertEquals("e3RleHR1cmVz=", Mayor.texture(json));
		assertNull(Mayor.texture("{}"));
	}
}
