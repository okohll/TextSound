/*
 *   Copyright 2012 Oliver Kohll
 * 
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gtwm.sound;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jfugue.MicrotoneNotation;
import org.jfugue.Player;

/**
 * Turn a stream of text into sound, using the overtone series Letter A = root
 * note B = first overtone, double the frequency, one octave above C = triple
 * the frequency, an octave plus a fifth above the root D = x4 frequency, two
 * octaves up E = x5, two octaves plus a third etc.
 * 
 * Higher notes are normalised down to fit in the octave range specified
 * 
 * @author oliver
 */
public class TextSound {

	static String instrument = "PIANO";

	static List<String> orderings = new ArrayList<String>(Arrays.asList("ETAONRISHDLFCMUGYPWBVKXJQZ","ABCDEFGHIJKLMNOPQRSTUVWXYZ"));

	// Starting settings
	// NB: If any values are set to exactly zero, they will be unable to
	// change throughout the generation
	//
	// How long to hold each note for
	static double noteLength = 1; // /1 = whole note (semibreve). /0.25 =
									// crotchet

	// How long to wait before playing the next note
	static double noteGap = 0.0001; // 1 / 32d; // 1/32 = good default, 0 = no

	// gap (chords)
	// How long to pause when a rest (space etc.) is encountered
	static double restLength = 1 / 10d; // 1/8 = good default

	// Lowest note that can be played
	static double baseFrequency = 128; // 128 Hz = Octave below middle C

	// Octave range in which to place notes
	static double octaves = 2;

	// Tempo in beats per minute
	static double tempo = 100;

	// Which letter ordering (defined above) to use, zero indexed
	static int ordering = 0;

	// Initial setting type
	static Setting setting = Setting.TEMPO;

	static EnumSet<Setting> allSettings = EnumSet.allOf(Setting.class);

	// Characters which prompt a change of setting type
	static String settingChangers = ".";

	// Even characters increase setting values, odd characters decrease.
	// This swaps that behaviour
	static boolean tempoDirection = false;

	// TODO: could use these to change and revert - opening bracket changes,
	// closing changes the same setting in the opposite direction
	static String containers = "(){}[]<>\"\"";

	// Will cycle.
	// 0 = increase attack, 1 = decrease attack, 2 = increase decay, 3 =
	// decrease decay
	static int attackDecayAction = 0;

	static double attack = 64;

	static double decay = 64;

	// Affects how quickly attack and decay values change. Higher value = slower
	// change
	static double attackDecayDamper = 8d;

	// Print out each paragraph as we play (causes a pause each time)
	static boolean follow = false;

	static Set<String> passingWords = new HashSet<String>(Arrays.asList("THE", "A", "AND", "OR",
			"NOT", "WITH", "THIS", "IN", "INTO", "IS", "THAT", "THEN", "OF", "BUT", "BY", "DID",
			"TO", "IT", "ALL", "WAS", "WENT", "ARE", "WERE"));

	enum Setting {
		NOTE_LENGTH(0.01, 8.0), ARPEGGIATE_GAP(0.001, 0.5), REST_LENGTH(0.01, 0.5), BASE_FREQUENCY(
				16.0, 2048), OCTAVES(1.0, 5.0), TEMPO(100, 1000), LETTER_ORDERING(0.0, 0.0);
		Setting(double min, double max) {
			if (min == 0) {
				// Don't allow absolute zero as a min otherwise will never
				// recover, i.e. it won't be able to be changed by
				// multiplication
				this.min = 0.0001;
			} else {
				this.min = min;
			}
			this.max = max;
		}

		public double keepInRange(double value) {
			if (value < this.min) {
				this.directionRollingAverage = (this.directionRollingAverage - 1d) / 2d;
				if (this.directionRollingAverage < -0.8) {
					System.out.println(this.toString() + " too low at " + value
							+ ", swapping direction. RA = " + this.directionRollingAverage);
					this.direction = !this.direction;
				}
				double returnValue = this.min + (this.min - value);
				if (returnValue > this.min && returnValue < this.max) {
					return returnValue;
				} else {
					System.out.println("" + this + " return value " + returnValue
							+ " out of range, instead "
							+ ((returnValue % (this.max - this.min)) + this.min));
					return (Math.abs(returnValue) % (this.max - this.min)) + this.min;
				}
			} else if (value > this.max) {
				this.directionRollingAverage = (this.directionRollingAverage + 1d) / 2d;
				if (this.directionRollingAverage > 0.8) {
					System.out.println(this.toString() + " too high at " + value
							+ ", swapping direction. RA = " + this.directionRollingAverage);
					this.direction = !this.direction;
				}
				double returnValue = this.max - (value - this.max);
				if (returnValue > this.min && returnValue < this.max) {
					return returnValue;
				} else {
					System.out.println("" + this + " return value " + returnValue
							+ " out of range, instead "
							+ ((returnValue % (this.max - this.min)) + this.min));
					return (Math.abs(returnValue) % (this.max - this.min)) + this.min;
				}
			} else {
				return value;
			}
		}

		public boolean getDirection() {
			return this.direction;
		}

		private double min;

		private double max;

		private boolean direction = true;

		private double directionRollingAverage = 0d;

	}

	public static void main(String[] args) throws Exception {

		// Default for testing purposes
		String inFilename = "/Users/oliver/Downloads/textSound.txt";
		if (args.length > 0) {
			inFilename = args[0];
			if (args.length > 1) {
				String followString = args[1];
				if (followString.toLowerCase().equals("follow")) {
					follow = true;
				}
			}
		}
		String outFilename = inFilename + ".mid";

		List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(inFilename),
				StandardCharsets.UTF_8);
		StringBuilder inBuilder = new StringBuilder();
		Player player = new Player();
		player.play("T" + (int) tempo + " I[" + instrument + "] ");
		String paraSoundString = "";
		String para = "";
		int lineCount = 0;
		for (String line : lines) {
			lineCount++;
			String theLine = line.replace("\r", "\n") + "\n";
			inBuilder.append(theLine);
			if (follow) {
				paraSoundString += processString(theLine);
				para += theLine;
				if (theLine.length() < 2) {
					System.out.println(para);
					player.play("T" + (int) tempo + " " + paraSoundString);
					paraSoundString = "";
					para = "";
				}
			}
		}
		if (follow) {
			// The last para
			System.out.println(para);
			player.play("T" + (int) tempo + " " + paraSoundString);
		} else {
			System.out.println();
			String input = inBuilder.toString();
			String ss = "T" + (int) tempo + " I[" + instrument + "] " + processString(input);
			System.out.println(input);
			player = new Player();
			File file = new File(outFilename);
			player.saveMidi(ss, file);
			player.play(ss);
		}
	}

	/**
	 * Turn the input string into a sound string that can be played by jFugue
	 */
	private static String processString(String input) {
		StringBuilder soundString = new StringBuilder();
		// For debugging / printout purposes
		StringBuilder lastSentence = new StringBuilder();
		// To allow word properties to influence sound
		StringBuilder lastWord = new StringBuilder();
		for (int charIndex = 0; charIndex < input.length(); charIndex++) {
			char ch = input.charAt(charIndex);
			char upperCh = Character.toUpperCase(ch);
			lastSentence.append(ch);
			String charString = String.valueOf(ch);
			// A = 1, B = 2, ...
			int charNum = orderings.get(ordering).indexOf(upperCh) + 1;
			if ((Character.isWhitespace(ch)) || (charNum < 1)) {
				double theRestLength = restLength;
				if (passingWords.contains(lastWord.toString())) {
					theRestLength = restLength * (2d / 3d);
				}
				lastWord.setLength(0);
				soundString.append("R/" + String.format("%f", theRestLength) + " ");
				if (charString.equals("\n")) {
					// An extra rest on newlines
					soundString.append("R/" + String.format("%f", restLength) + " ");
				}
				if (settingChangers.contains(charString)) {
					changeSetting();
				} else if (!Character.isWhitespace(ch)) {
					int ascii = (int) ch;
					boolean increase = (ascii % 2 == 0);
					// Stop things getting too extreme for too long
					if (!setting.getDirection()) {
						increase = !increase;
					}
					lastSentence.setLength(0);
					// Factor can be in the range 0.5..2: ASCII characters can
					// half or double the
					// existing value at the most. Other rarer characters can
					// have more of an effect
					double factor = 1 + (ascii / 127d);
					if (!increase) {
						factor = 1 / factor;
					}
					switch (setting) {
					case NOTE_LENGTH:
						noteLength = setting.keepInRange(noteLength * factor);
						break;
					case ARPEGGIATE_GAP:
						double oldNoteGap = noteGap;
						noteGap = setting.keepInRange(noteGap * factor);
						break;
					case REST_LENGTH:
						restLength = setting.keepInRange(restLength * factor);
						break;
					case BASE_FREQUENCY:
						baseFrequency = setting.keepInRange(baseFrequency * factor);
						break;
					case OCTAVES:
						octaves = setting.keepInRange(octaves * factor);
						break;
					case TEMPO:
						double oldTempo = tempo;
						tempo = setting.keepInRange(tempo * factor);
						soundString.append("T" + (int) tempo + " ");
						break;
					case LETTER_ORDERING:
						ordering += 1;
						if (ordering > (orderings.size() - 1)) {
							ordering = 0;
						}
						// Only change letter ordering once, then move on to
						// something else
						changeSetting();
						break;
					default:
						throw new IllegalStateException("Setting " + setting + " is not handled");
					}
				}
			} else {
				// The core of it: turn letters into frequencies
				changeAttackDecay(charNum);
				lastWord.append(upperCh);
				double targetOctave = Math.ceil((charNum / 26d) * octaves);
				double frequency = charNum * baseFrequency;
				// Normalise to fit in the range
				double topFrequency = baseFrequency;
				for (int j = 0; j < targetOctave; j++) {
					topFrequency = topFrequency * 2;
				}
				while (frequency > topFrequency) {
					frequency = frequency / 2;
				}
				soundString.append(MicrotoneNotation.convertFrequencyToMusicString(frequency));
				if (Character.isUpperCase(ch)) {
					soundString.append("/" + String.format("%f", noteLength * 4));
				} else {
					soundString.append("/" + String.format("%f", noteLength));
				}
				soundString.append("A" + (int) attack + "D" + (int) decay);
				double theNoteGap = noteGap;
				if (theNoteGap > 0.2) {
					theNoteGap = theNoteGap / lastWord.length();
				} else if ((theNoteGap > 0.1) && passingWords.contains(lastWord.toString())) {
					theNoteGap = theNoteGap * 0.5;
				}
				soundString.append("+R/" + String.format("%f", theNoteGap) + " ");
			}
		}
		return soundString.toString();
	}

	private static void changeSetting() {
		int newSettingNum = setting.ordinal() + 1;
		if (newSettingNum >= allSettings.size()) {
			newSettingNum = 0;
		}
		for (Setting testSetting : allSettings) {
			if (testSetting.ordinal() == newSettingNum) {
				setting = testSetting;
			}
		}
	}

	private static void changeAttackDecay(int charNum) {
		double changeAmount = (double) charNum / attackDecayDamper;
		attackDecayAction++;
		if (attackDecayAction > 3) {
			attackDecayAction = 0;
		}
		switch (attackDecayAction) {
		case 0:
			attack += changeAmount;
			break;
		case 1:
			attack -= changeAmount;
			break;
		case 2:
			decay += changeAmount;
			break;
		case 3:
			decay -= changeAmount;
			break;
		}
		if (attack > 127) {
			attack = 127 - (attack - 127);
		}
		if (attack < 20) {
			attack = 20 + (20 - attack);
		}
		if (decay > 127) {
			decay = 127 - (decay - 127);
		}
		if (decay < 20) {
			decay = 20 + (20 - decay);
		}
	}

}
