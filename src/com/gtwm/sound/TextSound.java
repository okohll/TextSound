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
import java.util.EnumSet;
import java.util.List;
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
	
	//TODO: could use these to change and revert - opening bracket changes, closing changes the same setting in the opposite direction
	static String containers = "(){}[]<>\"\"";

	enum Setting {
		NOTE_LENGTH(0, 2), NOTE_GAP(0, 2), REST_LENGTH(0, 2), BASE_FREQUENCY(32, 2048), OCTAVES(1,
				5), TEMPO(1, 1000);
		Setting(double min, double max) {
			if (min == 0) {
				// Don't allow absolute zero as a min otherwise will never recover,
				// i.e. it won't be able to be changed by multiplication
				this.min = 0.0001;
			} else {
				this.min = min;
			}
			this.max = max;
		}

		public double keepInRange(double value) {
			if (value < this.min) {
				return this.min;
			} else if (value > this.max) {
				return this.max;
			} else {
				return value;
			}
		}

		private double min;

		private double max;
	}

	public static void main(String[] args) throws Exception {

		// Default for testing purposes
		String inFilename = "/Users/oliver/Downloads/textSound.txt";
		if (args.length > 0) {
			inFilename = args[0];
		}
		String outFilename = inFilename + ".mid";
		
		// Starting settings
		// NB: If any values are set to exactly zero, they will be unable to change throughout the generation
		//
		// How long to hold each note for
		double noteLength = 2; // /1 = whole note (semibreve). /0.25 = crotchet
		// How long to wait before playing the next note
		double noteGap = 0.0001; // 1 / 32d; // 1/32 = good default, 0 = no
							// gap (chords)
		// How long to pause when a rest (space etc.) is encountered
		double restLength = 1/8d; // 1/8 = good default
		// Lowest note that can be played
		double baseFrequency = 64; // 128 Hz = Octave below middle C
		// Octave range in which to place notes
		double octaves = 3;
		// Tempo in beats per second
		double tempo = 100;
		// Initial setting type
		Setting setting = Setting.TEMPO;
		EnumSet<Setting> allSettings = EnumSet.allOf(Setting.class);
		// Characters which prompt a change of setting type
		String settingChangers = ".";

		List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(inFilename),
				StandardCharsets.UTF_8);
		StringBuilder inBuilder = new StringBuilder();
		for (String line : lines) {
			inBuilder.append(line);
		}
		String input = inBuilder.toString();
		// input = "It was the best of times, it was the worst of times";
		// input =
		// "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to heaven, we were all going direct the other way - in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.";
		// input =
		// "The quality of implementation specifications concern two properties, accuracy of the returned result and monotonicity of the method";
		// input =
		// "In certain service environments, particularly those where the business environment is dynamic and where clients interact with the organisation on a regular basis, we have found a grasp of Systems Thinking is invaluable.";
		// input =
		// "The Sixth Crusade started in 1228 as an attempt to regain Jerusalem";
		// input =
		// "Although Baden-Württemberg is lacking natural resources,[2] the state is among the most prosperous states in Germany";
		// input =
		// "If you want to print this guide, it is recommended that you print in Landscape mode";
		// input =
		// "Effective content marketing holds people’s attention. It gives you a distinctive brand, loyal fans and increased sales. You don’t need a big budget to succeed, which is why good content marketing is the single best way to beat bigger competitors online";
		// input =
		// "The municipality was created on 14 March 1997, succeeding the sub-provincial city administration that was part of Sichuan Province";
		StringBuilder soundString = new StringBuilder("T" + (int) tempo + " I[" + instrument + "] ");
		// For debugging / printout purposes
		StringBuilder lastSentence = new StringBuilder();
		for (int charIndex = 0; charIndex < input.length(); charIndex++) {
			char ch = input.charAt(charIndex);
			lastSentence.append(ch);
			String charString = String.valueOf(ch);
			// A = 1, B = 2, ...
			int charNum = Character.getNumericValue(Character.toUpperCase(ch)) - 9;

			if ((Character.isWhitespace(ch)) || (charNum < 1)) {
				soundString.append("R/" + restLength + " ");
				if (charString.equals("\n")) {
					// An extra rest on newlines
					soundString.append("R/" + restLength + " ");					
				}
				if (settingChangers.contains(charString)) {
					int newSettingNum = setting.ordinal() + 1;
					if (newSettingNum >= allSettings.size()) {
						newSettingNum = 0;
					}
					for (Setting testSetting : allSettings) {
						if (testSetting.ordinal() == newSettingNum) {
							setting = testSetting;
						}
					}
				} else if (!Character.isWhitespace(ch)) {
					int ascii = (int) ch;
					boolean increase = (ascii % 2 == 0);
					System.out.println(lastSentence);
					lastSentence.setLength(0);
					System.out.print("       " + ch + "(" + ascii + "): " + increase + ". ");
					// Factor can be in the range 0.5..2: can half or double the
					// existing value at the most
					double factor = 1 + (ascii / 127d);
					if (!increase) {
						factor = 1 / factor;
					}
					switch (setting) {
					case NOTE_LENGTH:
						noteLength = setting.keepInRange(noteLength * factor);
						System.out.println("Note length changed to " + String.format("%f",noteLength));
						break;
					case NOTE_GAP:
						noteGap = setting.keepInRange(noteGap * factor);
						System.out.println("Note gap changed to " + String.format("%f",noteGap));
						break;
					case REST_LENGTH:
						restLength = setting.keepInRange(restLength * factor);
						System.out.println("Rest length changed to " + String.format("%f",restLength));
						break;
					case BASE_FREQUENCY:
						baseFrequency = setting.keepInRange(baseFrequency * factor);
						System.out.println("Base frequency changed to " + baseFrequency);
						break;
					case OCTAVES:
						octaves = setting.keepInRange(octaves * factor);
						System.out.println("Octaves changed to " + octaves);
						break;
					case TEMPO:
						tempo = setting.keepInRange(tempo * factor);
						System.out.println("Tempo changed to " + tempo);
						soundString.append("T" + (int) tempo + " ");
						break;
					default:
						throw new IllegalStateException("Setting " + setting + " is not handled");
					}
				}
			} else {
				// The core of it: turn letters into frequencies
				double frequency = charNum * baseFrequency;
				// Normalise to fit in the range
				double topFrequency = baseFrequency;
				for (int j = 0; j < octaves; j++) {
					topFrequency = topFrequency * 2;
				}
				while (frequency > topFrequency) {
					frequency = frequency / 2;
				}
				//System.out.println("Frequency for " + ch + "=" + charNum + " normalized to octave "
				//		+ octaves + ", top frequency " + topFrequency + ": " + frequency);
				soundString.append(MicrotoneNotation.convertFrequencyToMusicString(frequency));
				if (Character.isUpperCase(ch)) {
					soundString.append("/" + String.format("%f",noteLength * 2));
				} else {
					soundString.append("/" + String.format("%f",noteLength));
				}
				soundString.append("+R/" + String.format("%f",noteGap) + " ");
			}
		}
		System.out.println(soundString);
		Player player = new Player();
		File file = new File(outFilename);
		String ss = soundString.toString();
		player.saveMidi(ss, file);
		player.play(ss);
	}

}
