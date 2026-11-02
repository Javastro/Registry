declare variable $rin as xs:string external; 

 (: this does not work ::)
for $item at $pos in fn:collection("Registry/harvest")//source[identifier=$rin]
return $item update {
  delete node ./lastAttempted/text(),
  replace value of node ./status with 'ACTIVE'
}

