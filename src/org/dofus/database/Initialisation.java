package org.dofus.database;

import org.dofus.database.objects.BreedsData;
import org.dofus.database.objects.ExperiencesData;

public class Initialisation {

	public static void init() {
		ExperiencesData.load();
		BreedsData.load();
	}
}
