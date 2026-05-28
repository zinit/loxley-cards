import braciaSherwood from '../assets/cards/final/bracia-z-sherwood.webp';
import lordWalter from '../assets/cards/final/lord-walter-z-huntingdon.webp';
import sirRichard from '../assets/cards/final/sir-richard-z-lea.webp';
import wiesniacy from '../assets/cards/final/wiesniacy-z-locksley.webp';
import allanADale from '../assets/cards/final/allan-a-dale.webp';
import ladyMarian from '../assets/cards/final/lady-marian.webp';
import malyJohn from '../assets/cards/final/maly-john.webp';
import willScarlet from '../assets/cards/final/will-scarlet.webp';
import lucznicy from '../assets/cards/final/lucznicy-z-sherwood.webp';
import much from '../assets/cards/final/much-syn-mlynarza.webp';
import eleanor from '../assets/cards/final/eleanor-luczniczka.webp';
import annabelle from '../assets/cards/final/annabelle-z-mansfield.webp';
import wielkiWillStutely from '../assets/cards/final/wielki-will-stutely.webp';
import bratTuck from '../assets/cards/final/brat-tuck.webp';
import inzynier from '../assets/cards/final/inzynier-z-sherwood.webp';
import hugoLapserdak from '../assets/cards/final/hugo-lapserdak.webp';
import trebusz from '../assets/cards/final/trebusz-sherwoodu.webp';
import wielkiLuk from '../assets/cards/final/wielki-luk.webp';
import taran from '../assets/cards/final/taran-buntownikow.webp';
import drewnianaWieza from '../assets/cards/final/drewniana-wieza.webp';
import robinHoodLeader from '../assets/cards/final/robin-hood-leader.webp';
import rogSherwoodu from '../assets/cards/final/rog-sherwoodu.webp';
import strachNaWroble from '../assets/cards/final/strach-na-wroble.webp';
import ognisteStrzaly from '../assets/cards/final/ogniste-strzaly.webp';
import lodowatySwit from '../assets/cards/final/lodowaty-swit.webp';
import mglaSherwood from '../assets/cards/final/mgla-sherwood.webp';
import burzaNadLasem from '../assets/cards/final/burza-nad-lasem.webp';
import sloneNadSherwood from '../assets/cards/final/slonce-nad-sherwood.webp';

export type CardRow = 'CLOSE' | 'RANGED' | 'SIEGE';
export type CardType = 'UNIT' | 'HERO' | 'SPECIAL' | 'LEADER';

export interface FinalCard {
  id: string;
  name: string;
  image: string;
  type: CardType;
  row: CardRow | null;
  power: number | null;
  ability: string | null;
}

export const FINAL_DECK: FinalCard[] = [
  { id: 'robin-hood-leader', name: 'Robin Hood: Łowca z Sherwood', image: robinHoodLeader, type: 'LEADER', row: null, power: null, ability: 'CLEAR_WEATHER' },

  { id: 'wiesniacy', name: 'Wieśniacy z Locksley', image: wiesniacy, type: 'UNIT', row: 'CLOSE', power: 1, ability: 'TIGHT_BOND' },
  { id: 'bracia', name: 'Bracia z Sherwood', image: braciaSherwood, type: 'UNIT', row: 'CLOSE', power: 4, ability: 'TIGHT_BOND' },
  { id: 'lord-walter', name: 'Lord Walter z Huntingdon', image: lordWalter, type: 'UNIT', row: 'CLOSE', power: 5, ability: 'SPY' },
  { id: 'allan-a-dale', name: 'Allan-a-Dale', image: allanADale, type: 'UNIT', row: 'CLOSE', power: 4, ability: 'SPY' },
  { id: 'lady-marian', name: 'Lady Marian', image: ladyMarian, type: 'UNIT', row: 'CLOSE', power: 5, ability: null },
  { id: 'sir-richard', name: 'Sir Richard z Lea', image: sirRichard, type: 'UNIT', row: 'CLOSE', power: 5, ability: null },

  { id: 'maly-john', name: 'Mały John', image: malyJohn, type: 'HERO', row: 'CLOSE', power: 10, ability: 'HERO' },
  { id: 'will-scarlet', name: 'Will Scarlet', image: willScarlet, type: 'HERO', row: 'CLOSE', power: 10, ability: 'HERO' },

  { id: 'lucznicy', name: 'Łucznicy z Sherwood', image: lucznicy, type: 'UNIT', row: 'RANGED', power: 5, ability: 'TIGHT_BOND' },
  { id: 'much', name: 'Much, Syn Młynarza', image: much, type: 'UNIT', row: 'RANGED', power: 6, ability: null },
  { id: 'eleanor', name: 'Eleanor Łuczniczka', image: eleanor, type: 'UNIT', row: 'RANGED', power: 5, ability: null },
  { id: 'annabelle', name: 'Annabelle z Mansfield', image: annabelle, type: 'UNIT', row: 'RANGED', power: 4, ability: null },

  { id: 'wielki-will-stutely', name: 'Wielki Will Stutely', image: wielkiWillStutely, type: 'HERO', row: 'RANGED', power: 10, ability: 'HERO' },

  { id: 'trebusz', name: 'Trebusz Sherwoodu', image: trebusz, type: 'UNIT', row: 'SIEGE', power: 8, ability: 'TIGHT_BOND' },
  { id: 'wielki-luk', name: 'Wielki Łuk', image: wielkiLuk, type: 'UNIT', row: 'SIEGE', power: 6, ability: null },
  { id: 'taran', name: 'Taran Buntowników', image: taran, type: 'UNIT', row: 'SIEGE', power: 6, ability: null },
  { id: 'drewniana-wieza', name: 'Drewniana Wieża', image: drewnianaWieza, type: 'UNIT', row: 'SIEGE', power: 6, ability: null },
  { id: 'brat-tuck', name: 'Brat Tuck', image: bratTuck, type: 'UNIT', row: 'SIEGE', power: 5, ability: 'MEDIC' },
  { id: 'inzynier', name: 'Inżynier z Sherwood', image: inzynier, type: 'UNIT', row: 'SIEGE', power: 1, ability: 'MORALE_BOOST' },
  { id: 'hugo', name: 'Hugo Łapserdak', image: hugoLapserdak, type: 'UNIT', row: 'SIEGE', power: 1, ability: 'SPY' },

  { id: 'rog', name: 'Róg Sherwoodu', image: rogSherwoodu, type: 'SPECIAL', row: null, power: null, ability: 'COMMANDERS_HORN' },
  { id: 'strach', name: 'Strach na Wróble', image: strachNaWroble, type: 'SPECIAL', row: null, power: null, ability: 'DECOY' },
  { id: 'ogniste-strzaly', name: 'Ogniste Strzały', image: ognisteStrzaly, type: 'SPECIAL', row: null, power: null, ability: 'SCORCH' },
  { id: 'lodowaty-swit', name: 'Lodowaty Świt', image: lodowatySwit, type: 'SPECIAL', row: null, power: null, ability: 'WEATHER_CLOSE' },
  { id: 'mgla-sherwood', name: 'Mgła Sherwood', image: mglaSherwood, type: 'SPECIAL', row: null, power: null, ability: 'WEATHER_RANGED' },
  { id: 'burza', name: 'Burza nad Lasem', image: burzaNadLasem, type: 'SPECIAL', row: null, power: null, ability: 'WEATHER_SIEGE' },
  { id: 'slonce', name: 'Słońce nad Sherwood', image: sloneNadSherwood, type: 'SPECIAL', row: null, power: null, ability: 'CLEAR_WEATHER' },
];
