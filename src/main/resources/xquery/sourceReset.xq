declare variable $source as xs:string external; 


 
delete node  fn:collection("Registry/harvest")//source[identifier=$source]/lastAttempted/text(),
replace value of node fn:collection("Registry/harvest")//source[identifier=$source]/status with 'ACTIVE'
