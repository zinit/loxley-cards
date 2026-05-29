import robinPlaceholder from '../assets/cards/robin-placeholder.webp'
import braciaSherwood from '../assets/cards/final/bracia-z-sherwood.webp'
import lordWalter from '../assets/cards/final/lord-walter-z-huntingdon.webp'
import sirRichard from '../assets/cards/final/sir-richard-z-lea.webp'
import wiesniacy from '../assets/cards/final/wiesniacy-z-locksley.webp'
import allanADale from '../assets/cards/final/allan-a-dale.webp'
import ladyMarian from '../assets/cards/final/lady-marian.webp'
import malyJohn from '../assets/cards/final/maly-john.webp'
import willScarlet from '../assets/cards/final/will-scarlet.webp'
import lucznicy from '../assets/cards/final/lucznicy-z-sherwood.webp'
import much from '../assets/cards/final/much-syn-mlynarza.webp'
import eleanor from '../assets/cards/final/eleanor-luczniczka.webp'
import annabelle from '../assets/cards/final/annabelle-z-mansfield.webp'
import wielkiWillStutely from '../assets/cards/final/wielki-will-stutely.webp'
import bratTuck from '../assets/cards/final/brat-tuck.webp'
import inzynier from '../assets/cards/final/inzynier-z-sherwood.webp'
import hugoLapserdak from '../assets/cards/final/hugo-lapserdak.webp'
import trebusz from '../assets/cards/final/trebusz-sherwoodu.webp'
import wielkiLuk from '../assets/cards/final/wielki-luk.webp'
import taran from '../assets/cards/final/taran-buntownikow.webp'
import drewnianaWieza from '../assets/cards/final/drewniana-wieza.webp'
import robinHoodLeader from '../assets/cards/final/robin-hood-leader.webp'
import rogSherwoodu from '../assets/cards/final/rog-sherwoodu.webp'
import strachNaWroble from '../assets/cards/final/strach-na-wroble.webp'
import ognisteStrzaly from '../assets/cards/final/ogniste-strzaly.webp'
import lodowatySwit from '../assets/cards/final/lodowaty-swit.webp'
import mglaSherwood from '../assets/cards/final/mgla-sherwood.webp'
import burzaNadLasem from '../assets/cards/final/burza-nad-lasem.webp'
import sloneNadSherwood from '../assets/cards/final/slonce-nad-sherwood.webp'

/**
 * Maps backend card IDs (English snake_case from robin_logic.json)
 * to frontend WebP image imports.
 */
const CARD_IMAGE_MAP: Record<string, string> = {
  leader_robin_hood_sherwood_hunter: robinHoodLeader,
  peasants_of_locksley: wiesniacy,
  sherwood_brothers: braciaSherwood,
  lord_walter_huntingdon: lordWalter,
  allan_a_dale: allanADale,
  little_john: malyJohn,
  will_scarlet: willScarlet,
  lady_marian: ladyMarian,
  sir_richard_lea: sirRichard,
  sherwood_archers: lucznicy,
  much_millers_son: much,
  eleanor_archer: eleanor,
  annabelle_mansfield: annabelle,
  will_stutely: wielkiWillStutely,
  sherwood_trebuchet: trebusz,
  great_bow: wielkiLuk,
  rebels_ram: taran,
  wooden_tower: drewnianaWieza,
  friar_tuck: bratTuck,
  sherwood_engineer: inzynier,
  hugo_rascal: hugoLapserdak,
  sherwood_horn: rogSherwoodu,
  scarecrow: strachNaWroble,
  fire_arrows: ognisteStrzaly,
  icy_dawn: lodowatySwit,
  sherwood_fog: mglaSherwood,
  forest_storm: burzaNadLasem,
  sherwood_sun: sloneNadSherwood,
}

export function getCardImage(cardId: string): string {
  return CARD_IMAGE_MAP[cardId] ?? robinPlaceholder
}
