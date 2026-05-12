package org.dofus.objects.actors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.dofus.constants.EConstants;
import org.dofus.database.objects.BreedsData;
import org.dofus.network.game.BotClient;
import org.dofus.database.objects.ExperiencesData;
import org.dofus.database.objects.MapsData;
import org.dofus.network.game.protocols.GProtocol;
import org.dofus.objects.WorldData;
import org.dofus.objects.accounts.Account;
import org.dofus.objects.characters.Restriction;
import org.dofus.objects.characters.Right;
import org.dofus.objects.characters.Statistic;
import org.dofus.objects.experiences.AlignmentExperience;
import org.dofus.objects.experiences.CharacterExperience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotAI {

	private static final Logger logger = LoggerFactory.getLogger(BotAI.class);

	// IDs négatifs uniques pour ne pas entrer en conflit avec les vrais personnages
	private static final AtomicInteger BOT_ID = new AtomicInteger(-1000);

	/** Personnalité assignée à chaque bot (botId → personnalité). */
	private static final Map<Integer, BotPersonality> personalities = new ConcurrentHashMap<>();

	/**
	 * Configuration des bots.
	 * [name, mapId, cellId, breedId, gender, color1, color2, color3, level, BotPersonality]
	 */
	private static final Object[][] BOT_CONFIGS = {
		    { "Alynandra", 935, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Alynanor", 528, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Alynalis", 9454, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Alynax", 951, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Alynenne", 1242, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Alynthar", 164, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Alynia", 1158, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Alynar", 8037, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Alynith", 8437, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Alynath", 8088, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Alynor", 8125, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Alyniel", 8163, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Alynaen", 10643, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Alynwyn", 11170, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Alyndil", 1841, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Alynmos", 844, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Alynrune", 11210, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Alynvex", 4263, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
/**		    { "Alyndros", 3022, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Alynlune", 6855, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Alynmir", 6137, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Alyndane", 3250, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Alynvar", 4739, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Alynsyl", 5295, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Alynkhan", 8785, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Alynra", 7411, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Alynmon", 6954, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Alynriel", 2191, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Alyngorn", 10297, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Alynphel", 10349, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Alynnyx", 10304, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Alynsor", 10317, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Alyntan", 10114, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Alynvyr", 4271, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Alynlith", 4174, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Alyndor", 8758, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Alynmare", 4299, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Alynnox", 4180, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Alynbryn", 8759, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Alynka", 4183, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Torvandra", 2221, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Torvanor", 4308, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Torvalis", 4217, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Torvax", 4098, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Torvenne", 8757, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Torvthar", 4223, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Torvia", 8760, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Torvar", 2214, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Torvith", 4179, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Torvath", 4229, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Torvor", 4232, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Torviel", 8478, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Torvaen", 4238, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Torvwyn", 4216, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Torvdil", 6159, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Torvmos", 4172, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Torvrune", 4247, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Torvvex", 4272, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Torvdros", 4250, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Torvlune", 4178, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Torvmir", 4106, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Torvdane", 4181, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Torvvar", 4259, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Torvsyl", 4090, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Torvkhan", 4262, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Torvra", 4287, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Torvmon", 4300, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Torvriel", 4240, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Torvgorn", 4218, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Torvphel", 4074, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Torvnyx", 6167, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Torvsor", 4930, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Torvtan", 4620, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Torvvyr", 4604, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Torvlith", 4639, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Torvdor", 4627, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Torvmare", 4579, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Torvnox", 8756, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Torvbryn", 5277, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Torvka", 5304, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Myraandra", 5334, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Myraanor", 4612, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Myraalis", 4549, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Myraax", 4607, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Myraenne", 8753, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Myrathar", 4622, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Myraia", 4565, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Myraar", 5112, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Myraith", 4562, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Myraath", 8754, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Myraor", 5317, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Myraiel", 4615, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Myraaen", 4618, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Myrawyn", 4588, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Myradil", 8493, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Myramos", 4646, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Myrarune", 5332, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Myravex", 8755, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Myradros", 5116, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Myralune", 4601, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Myramir", 4637, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Myradane", 4623, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Myravar", 4551, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Myrasyl", 935, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Myrakhan", 528, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Myrara", 9454, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Myramon", 951, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Myrariel", 1242, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Myragorn", 164, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Myraphel", 1158, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Myranyx", 8037, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Myrasor", 8437, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Myratan", 8088, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Myravyr", 8125, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Myralith", 8163, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Myrador", 10643, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Myramare", 11170, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Myranox", 1841, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Myrabryn", 844, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Myraka", 11210, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brutandra", 4263, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brutanor", 3022, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brutalis", 6855, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brutax", 6137, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brutenne", 3250, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brutthar", 4739, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brutia", 5295, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brutar", 8785, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brutith", 7411, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brutath", 6954, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brutor", 2191, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brutiel", 10297, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brutaen", 10349, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brutwyn", 10304, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brutdil", 10317, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brutmos", 10114, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brutrune", 4271, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brutvex", 4174, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brutdros", 8758, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brutlune", 4299, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brutmir", 4180, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brutdane", 8759, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brutvar", 4183, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brutsyl", 2221, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brutkhan", 4308, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brutra", 4217, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brutmon", 4098, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brutriel", 8757, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brutgorn", 4223, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brutphel", 8760, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brutnyx", 2214, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brutsor", 4179, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Bruttan", 4229, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brutvyr", 4232, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brutlith", 8478, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brutdor", 4238, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brutmare", 4216, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brutnox", 6159, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brutbryn", 4172, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brutka", 4247, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Selenandra", 4272, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Selenanor", 4250, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Selenalis", 4178, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Selenax", 4106, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Selenenne", 4181, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Selenthar", 4259, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Selenia", 4090, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Selenar", 4262, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Selenith", 4287, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Selenath", 4300, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Selenor", 4240, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Seleniel", 4218, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Selenaen", 4074, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Selenwyn", 6167, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Selendil", 4930, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Selenmos", 4620, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Selenrune", 4604, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Selenvex", 4639, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Selendros", 4627, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Selenlune", 4579, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Selenmir", 8756, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Selendane", 5277, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Selenvar", 5304, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Selensyl", 5334, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Selenkhan", 4612, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Selenra", 4549, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Selenmon", 4607, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Selenriel", 8753, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Selengorn", 4622, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Selenphel", 4565, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Selennyx", 5112, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Selensor", 4562, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Selentan", 8754, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Selenvyr", 5317, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Selenlith", 4615, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Selendor", 4618, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Selenmare", 4588, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Selennox", 8493, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Selenbryn", 4646, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Selenka", 5332, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Drakandra", 8755, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Drakanor", 5116, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Drakalis", 4601, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Drakax", 4637, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Drakenne", 4623, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Drakthar", 4551, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Drakia", 935, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Drakar", 528, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Drakith", 9454, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Drakath", 951, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Drakor", 1242, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Drakiel", 164, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Drakaen", 1158, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Drakwyn", 8037, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Drakdil", 8437, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Drakmos", 8088, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Drakrune", 8125, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Drakvex", 8163, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Drakdros", 10643, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Draklune", 11170, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Drakmir", 1841, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Drakdane", 844, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Drakvar", 11210, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Draksyl", 4263, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Drakkhan", 3022, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Drakra", 6855, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Drakmon", 6137, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Drakriel", 3250, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Drakgorn", 4739, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Drakphel", 5295, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Draknyx", 8785, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Draksor", 7411, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Draktan", 6954, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Drakvyr", 2191, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Draklith", 10297, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Drakdor", 10349, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Drakmare", 10304, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Draknox", 10317, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Drakbryn", 10114, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Drakka", 4271, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Lyrandra", 4174, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Lyranor", 8758, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Lyralis", 4299, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Lyrax", 4180, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Lyrenne", 8759, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Lyrthar", 4183, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Lyria", 2221, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Lyrar", 4308, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Lyrith", 4217, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Lyrath", 4098, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Lyror", 8757, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Lyriel", 4223, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Lyraen", 8760, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Lyrwyn", 2214, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Lyrdil", 4179, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Lyrmos", 4229, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Lyrrune", 4232, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Lyrvex", 8478, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Lyrdros", 4238, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Lyrlune", 4216, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Lyrmir", 6159, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Lyrdane", 4172, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Lyrvar", 4247, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Lyrsyl", 4272, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Lyrkhan", 4250, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Lyrra", 4178, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Lyrmon", 4106, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Lyrriel", 4181, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Lyrgorn", 4259, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Lyrphel", 4090, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Lyrnyx", 4262, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Lyrsor", 4287, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Lyrtan", 4300, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Lyrvyr", 4240, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Lyrlith", 4218, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Lyrdor", 4074, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Lyrmare", 6167, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Lyrnox", 4930, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Lyrbryn", 4620, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Lyrka", 4604, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Gondandra", 4639, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Gondanor", 4627, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Gondalis", 4579, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Gondax", 8756, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Gondenne", 5277, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Gondthar", 5304, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Gondia", 5334, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Gondar", 4612, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Gondith", 4549, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Gondath", 4607, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Gondor", 8753, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Gondiel", 4622, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Gondaen", 4565, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Gondwyn", 5112, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Gonddil", 4562, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Gondmos", 8754, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Gondrune", 5317, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Gondvex", 4615, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Gonddros", 4618, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Gondlune", 4588, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Gondmir", 8493, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Gonddane", 4646, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Gondvar", 5332, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Gondsyl", 8755, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Gondkhan", 5116, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Gondra", 4601, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Gondmon", 4637, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Gondriel", 4623, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Gondgorn", 4551, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Gondphel", 935, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Gondnyx", 528, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Gondsor", 9454, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Gondtan", 951, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Gondvyr", 1242, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Gondlith", 164, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Gonddor", 1158, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Gondmare", 8037, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Gondnox", 8437, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Gondbryn", 8088, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Gondka", 8125, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Vaelandra", 8163, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Vaelanor", 10643, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Vaelalis", 11170, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Vaelax", 1841, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Vaelenne", 844, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Vaelthar", 11210, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Vaelia", 4263, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Vaelar", 3022, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Vaelith", 6855, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Vaelath", 6137, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Vaelor", 3250, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Vaeliel", 4739, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Vaelaen", 5295, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Vaelwyn", 8785, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Vaeldil", 7411, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Vaelmos", 6954, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Vaelrune", 2191, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Vaelvex", 10297, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Vaeldros", 10349, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Vaellune", 10304, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Vaelmir", 10317, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Vaeldane", 10114, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Vaelvar", 4271, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Vaelsyl", 4174, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Vaelkhan", 8758, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Vaelra", 4299, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Vaelmon", 4180, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Vaelriel", 8759, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Vaelgorn", 4183, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Vaelphel", 2221, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Vaelnyx", 4308, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Vaelsor", 4217, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Vaeltan", 4098, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Vaelvyr", 8757, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Vaellith", 4223, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Vaeldor", 8760, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Vaelmare", 2214, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Vaelnox", 4179, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Vaelbryn", 4229, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Vaelka", 4232, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Orlandra", 8478, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Orlanor", 4238, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Orlalis", 4216, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Orlax", 6159, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Orlenne", 4172, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Orlthar", 4247, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Orlia", 4272, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Orlar", 4250, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Orlith", 4178, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Orlath", 4106, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Orlor", 4181, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Orliel", 4259, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Orlaen", 4090, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Orlwyn", 4262, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Orldil", 4287, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Orlmos", 4300, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Orlrune", 4240, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Orlvex", 4218, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Orldros", 4074, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Orllune", 6167, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Orlmir", 4930, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Orldane", 4620, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Orlvar", 4604, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Orlsyl", 4639, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Orlkhan", 4627, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Orlra", 4579, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Orlmon", 8756, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Orlriel", 5277, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Orlgorn", 5304, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Orlphel", 5334, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Orlnyx", 4612, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Orlsor", 4549, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Orltan", 4607, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Orlvyr", 8753, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Orllith", 4622, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Orldor", 4565, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Orlmare", 5112, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Orlnox", 4562, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Orlbryn", 8754, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Orlka", 5317, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kaelandra", 10297, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kaelanor", 10349, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kaelalis", 10304, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kaelax", 10317, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kaelenne", 10114, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kaelthar", 4271, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kaelia", 4174, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kaelar", 8758, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kaelith", 4299, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kaelath", 4180, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kaelor", 8759, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kaeliel", 4183, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kaelaen", 2221, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kaelwyn", 4308, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kaeldil", 4217, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kaelmos", 4098, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kaelrune", 8757, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kaelvex", 4223, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kaeldros", 8760, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kaellune", 2214, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kaelmir", 4179, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kaeldane", 4229, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kaelvar", 4232, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kaelsyl", 8478, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kaelkhan", 4238, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kaelra", 4216, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kaelmon", 6159, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kaelriel", 4172, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kaelgorn", 4247, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kaelphel", 4272, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kaelnyx", 4250, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kaelsor", 4178, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kaeltan", 4106, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kaelvyr", 4181, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kaellith", 4259, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kaeldor", 4090, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kaelmare", 4262, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kaelnox", 4287, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kaelbryn", 4300, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kaelka", 4240, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Tharandra", 4218, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Tharanor", 4074, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Tharalis", 6167, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Tharax", 4930, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Tharenne", 4620, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Tharthar", 4604, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Tharia", 4639, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Tharar", 4627, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Tharith", 4579, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Tharath", 8756, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Tharor", 5277, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Thariel", 5304, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Tharaen", 5334, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Tharwyn", 4612, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Thardil", 4549, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Tharmos", 4607, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Tharrune", 8753, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Tharvex", 4622, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Thardros", 4565, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Tharlune", 5112, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Tharmir", 4562, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Thardane", 8754, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Tharvar", 5317, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Tharsyl", 4615, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Tharkhan", 4618, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Tharra", 4588, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Tharmon", 8493, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Tharriel", 4646, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Thargorn", 5332, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Tharphel", 8755, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Tharnyx", 5116, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Tharsor", 4601, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Thartan", 4637, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Tharvyr", 4623, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Tharlith", 4551, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Thardor", 935, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Tharmare", 528, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Tharnox", 9454, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Tharbryn", 951, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Tharka", 1242, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Elyandra", 164, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Elyanor", 1158, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Elyalis", 8037, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Elyax", 8437, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Elyenne", 8088, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Elythar", 8125, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Elyia", 8163, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Elyar", 10643, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Elyith", 11170, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Elyath", 1841, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Elyor", 844, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Elyiel", 11210, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Elyaen", 4263, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Elywyn", 3022, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Elydil", 6855, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Elymos", 6137, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Elyrune", 3250, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Elyvex", 4739, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Elydros", 5295, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Elylune", 8785, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Elymir", 7411, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Elydane", 6954, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Elyvar", 2191, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Elysyl", 10297, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Elykhan", 10349, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Elyra", 10304, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Elymon", 10317, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Elyriel", 10114, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Elygorn", 4271, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Elyphel", 4174, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Elynyx", 8758, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Elysor", 4299, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Elytan", 4180, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Elyvyr", 8759, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Elylith", 4183, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Elydor", 2221, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Elymare", 4308, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Elynox", 4217, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Elybryn", 4098, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Elyka", 8757, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brinandra", 4223, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brinanor", 8760, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brinalis", 2214, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brinax", 4179, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brinenne", 4229, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brinthar", 4232, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brinia", 8478, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brinar", 4238, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brinith", 4216, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brinath", 6159, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brinor", 4172, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Briniel", 4247, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brinaen", 4272, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brinwyn", 4250, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brindil", 4178, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brinmos", 4106, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brinrune", 4181, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brinvex", 4259, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brindros", 4090, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brinlune", 4262, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brinmir", 4287, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brindane", 4300, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brinvar", 4240, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brinsyl", 4218, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brinkhan", 4074, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brinra", 6167, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brinmon", 4930, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brinriel", 4620, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Bringorn", 4604, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brinphel", 4639, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Brinnyx", 4627, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Brinsor", 4579, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Brintan", 8756, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Brinvyr", 5277, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Brinlith", 5304, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Brindor", 5334, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Brinmare", 4612, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Brinnox", 4549, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Brinbryn", 4607, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Brinka", 8753, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Zorandra", 4622, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Zoranor", 4565, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Zoralis", 5112, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Zorax", 4562, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Zorenne", 8754, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Zorthar", 5317, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Zoria", 4615, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Zorar", 4618, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Zorith", 4588, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Zorath", 8493, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Zoror", 4646, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Zoriel", 5332, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Zoraen", 8755, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Zorwyn", 5116, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Zordil", 4601, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Zormos", 4637, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Zorrune", 4623, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Zorvex", 4551, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Zordros", 935, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Zorlune", 528, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Zormir", 9454, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Zordane", 951, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Zorvar", 1242, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Zorsyl", 164, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Zorkhan", 1158, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Zorra", 8037, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Zormon", 8437, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Zorriel", 8088, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Zorgorn", 8125, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Zorphel", 8163, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Zornyx", 10643, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Zorsor", 11170, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Zortan", 1841, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Zorvyr", 844, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Zorlith", 11210, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Zordor", 4263, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Zormare", 3022, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Zornox", 6855, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Zorbryn", 6137, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Zorka", 3250, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Maloandra", 4739, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Maloanor", 5295, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Maloalis", 8785, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Maloax", 7411, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Maloenne", 6954, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Malothar", 2191, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Maloia", 10297, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Maloar", 10349, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Maloith", 10304, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Maloath", 10317, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Maloor", 10114, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Maloiel", 4271, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Maloaen", 4174, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Malowyn", 8758, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Malodil", 4299, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Malomos", 4180, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Malorune", 8759, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Malovex", 4183, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Malodros", 2221, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Malolune", 4308, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Malomir", 4217, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Malodane", 4098, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Malovar", 8757, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Malosyl", 4223, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Malokhan", 8760, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Malora", 2214, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Malomon", 4179, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Maloriel", 4229, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Malogorn", 4232, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Malophel", 8478, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Malonyx", 4238, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Malosor", 4216, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Malotan", 6159, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Malovyr", 4172, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Malolith", 4247, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Malodor", 4272, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Malomare", 4250, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Malonox", 4178, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Malobryn", 4106, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Maloka", 4181, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kyrandra", 4259, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kyranor", 4090, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kyralis", 4262, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kyrax", 4287, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kyrenne", 4300, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kyrthar", 4240, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kyria", 4218, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kyrar", 4074, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kyrith", 6167, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kyrath", 4930, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kyror", 4620, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kyriel", 4604, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kyraen", 4639, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kyrwyn", 4627, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kyrdil", 4579, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kyrmos", 8756, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kyrrune", 5277, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kyrvex", 5304, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kyrdros", 5334, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kyrlune", 4612, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kyrmir", 4549, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kyrdane", 4607, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kyrvar", 8753, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kyrsyl", 4622, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kyrkhan", 4565, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kyrra", 5112, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kyrmon", 4562, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kyrriel", 8754, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kyrgorn", 5317, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kyrphel", 4615, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Kyrnyx", 4618, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Kyrsor", 4588, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Kyrtan", 8493, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Kyrvyr", 4646, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Kyrlith", 5332, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Kyrdor", 8755, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Kyrmare", 5116, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Kyrnox", 4601, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Kyrbryn", 4637, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Kyrka", 4623, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Fendandra", 4551, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Fendanor", 935, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Fendalis", 528, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Fendax", 9454, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Fendenne", 951, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Fendthar", 1242, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Fendia", 164, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Fendar", 1158, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Fendith", 8037, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Fendath", 8437, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Fendor", 8088, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Fendiel", 8125, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Fendaen", 8163, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Fendwyn", 10643, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Fenddil", 11170, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Fendmos", 1841, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Fendrune", 844, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Fendvex", 11210, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		    { "Fenddros", 4263, (short) 230, (byte) 12, (byte) 1, 0x2EC4B6, 0xE9C46A, 0xF4A261, (short) 88, BotPersonality.EXPLORER }, // Pandawa F
		    { "Fendlune", 3022, (short) 400, (byte) 1, (byte) 0, 0x8338EC, 0x3A86FF, 0xFFBE0B, (short) 29, BotPersonality.MERCHANT }, // Feca M
		    { "Fendmir", 6855, (short) 270, (byte) 3, (byte) 1, 0xC84B00, 0xFFD700, 0x5A3E2B, (short) 50, BotPersonality.SOCIAL }, // Eniripsa F
		    { "Fenddane", 6137, (short) 300, (byte) 8, (byte) 0, 0x1A1A2E, 0xE94560, 0x0F3460, (short) 78, BotPersonality.EXPLORER }, // Cra M
		    { "Fendvar", 3250, (short) 320, (byte) 2, (byte) 1, 0x2D6A4F, 0x52B788, 0x95D5B2, (short) 34, BotPersonality.SOCIAL }, // Osamodas F
		    { "Fendsyl", 4739, (short) 250, (byte) 9, (byte) 0, 0x6B2737, 0xC9184A, 0xFF4D6D, (short) 95, BotPersonality.WARRIOR }, // Sacrieur M
		    { "Fendkhan", 5295, (short) 340, (byte) 6, (byte) 1, 0x4361EE, 0x3A0CA3, 0x7209B7, (short) 21, BotPersonality.EXPLORER }, // Ecaflip F
		    { "Fendra", 8785, (short) 360, (byte) 4, (byte) 0, 0x212529, 0x495057, 0xADB5BD, (short) 112, BotPersonality.MERCHANT }, // Sram M
		    { "Fendmon", 7411, (short) 380, (byte) 5, (byte) 1, 0xFF9F1C, 0xFFBF69, 0xCBF3F0, (short) 67, BotPersonality.SOCIAL }, // Xelor F
		    { "Fendriel", 6954, (short) 210, (byte) 7, (byte) 0, 0x606C38, 0xDDA15E, 0xBC6C25, (short) 43, BotPersonality.WARRIOR }, // Iop M
		**/
		    };

	/** Retourne la personnalité d'un bot, ou null si inconnu. */
	public static BotPersonality getPersonality(int botId) {
		return personalities.get(botId);
	}

	/** Crée et démarre tous les bots configurés. */
	public static void spawnAll() throws Exception {
		// botId → bot, pour construire les groupes d'amis après spawn
		java.util.Map<Integer, Characters> spawned = new java.util.LinkedHashMap<>();

		for(Object[] cfg : BOT_CONFIGS) {
			String      name   = (String)      cfg[0];
			int         mapId  = (int)         cfg[1];
			short       cellId = (short)       cfg[2];
			byte        breed  = (byte)        cfg[3];
			byte        gender = (byte)        cfg[4];
			int         c1     = (int)         cfg[5];
			int         c2     = (int)         cfg[6];
			int         c3     = (int)         cfg[7];
			short       level  = (short)       cfg[8];
			BotPersonality pers = (BotPersonality) cfg[9];

			try {
				Characters bot = create(name, mapId, cellId, breed, gender, c1, c2, c3, level);
				personalities.put(bot.getId(), pers);
				spawned.put(bot.getId(), bot);
				BotBehavior.start(bot);
			} catch(Exception e) {
				logger.error("Failed to spawn bot {}: {}", name, e.getMessage());
			}
		}

		// ── Groupes d'amis par personnalité ─────────────────────────────────
		// Les bots de même personnalité se connaissent et peuvent se suivre.
		java.util.Map<BotPersonality, java.util.List<Integer>> byPersonality = new java.util.EnumMap<>(BotPersonality.class);
		for(java.util.Map.Entry<Integer, Characters> entry : spawned.entrySet()) {
			BotPersonality p = personalities.get(entry.getKey());
			if(p != null)
				byPersonality.computeIfAbsent(p, k -> new java.util.ArrayList<>()).add(entry.getKey());
		}
		for(java.util.List<Integer> group : byPersonality.values()) {
			int[] ids = group.stream().mapToInt(Integer::intValue).toArray();
			if(ids.length >= 2) BotSocial.addFriendGroup(ids);
		}
		// Liens inter-personnalités : SOCIAL ↔ EXPLORER (les curieux aiment discuter)
		java.util.List<Integer> socials   = byPersonality.getOrDefault(BotPersonality.SOCIAL,   java.util.Collections.emptyList());
		java.util.List<Integer> explorers = byPersonality.getOrDefault(BotPersonality.EXPLORER, java.util.Collections.emptyList());
		for(int s : socials)
			for(int e : explorers)
				BotSocial.addFriendship(s, e);

		logger.info("{} bots spawned, groupes d'amis enregistrés", spawned.size());
	}

	private static Characters create(String name, int mapId, short cellId,
			byte breedId, byte gender, int color1, int color2, int color3, short level) throws Exception {

		int botId = BOT_ID.getAndDecrement();

		Account account = new Account(botId, "bot_" + name, "", "", "", name, false);

		Characters bot = new Characters(
				botId,
				account,
				name,
				BreedsData.get(breedId),
				gender,
				color1, color2, color3,
				(short) (breedId * 10 + gender),
				EConstants.DEFAULT_SIZE.getShort(),
				MapsData.findById(mapId),
				cellId,
				EOrientation.SOUTH_EAST,
				new Right(8192),
				new Restriction(0),
				(short)(BreedsData.get(breedId).getLife() + 5 * (level - 1)),
				(short) 10000,
				null,
				0,
				new ConcurrentHashMap<>(),
				(short) 0,
				(short) 0,
				(byte) 0,
				null,
				false
		);

		bot.setExperience(new CharacterExperience(
				level,
				ExperiencesData.get(level).getCharacter(),
				ExperiencesData.get(level),
				bot));

		bot.setAlignment(new AlignmentExperience(
				(short) 0, 0L, (byte) 0,
				ExperiencesData.get(EConstants.DEFAULT_LEVEL.getShort()),
				bot));

		bot.setStats(new Statistic(bot));
		bot.setConnected(true);

		// Donne aux bots un équipement cohérent avec leur niveau/classe.
		// L'inventaire reste temporaire : pas d'INSERT SQL pour ces bots éphémères.
		BotEquipmentService.equipForLevelAndBreed(bot);

		bot.getCurrentMap().addActor(bot);
		WorldData.addCharacterById(bot, bot.getId());
		WorldData.addCharacterByName(bot, bot.getName());

		// Enregistrer comme client complet → bot visible et interactif
		BotClient botClient = new BotClient(bot);
		botClient.register();

		broadcast(bot);
		logger.info("Bot {} (id={} breed={} lvl={}) spawned on map {}",
			new Object[]{ name, botId, breedId, level, mapId });
		return bot;
	}

	private static void broadcast(Characters bot) {
		StringBuilder packet = new StringBuilder("GM|+");
		GProtocol.getCharacterPattern(packet, bot);
		String packetStr = packet.toString();
		for(Characters actor : bot.getCurrentMap().getActors().values()) {
			if(actor == bot) continue;
			org.apache.mina.core.session.IoSession session =
				WorldData.getSessionByAccount().get(actor.getOwner());
			if(session != null && session.isConnected())
				session.write(packetStr);
		}
	}
}
