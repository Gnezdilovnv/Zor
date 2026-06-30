package com.zor.monitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: Map<String,String>, dirs: List<String>, pts: List<String>, types: List<String>, onSave: (Map<String,String>,Map<String,List<String>>)->Unit, onBack: ()->Unit) {
    var sd by remember{mutableStateOf(s["direction"]?: "")}; var sp by remember{mutableStateOf(s["point"]?: "")}
    var systemTheme by remember{mutableStateOf(s["system_theme"] != "false")}
    var fm by remember{mutableStateOf(s["export_format"]?: "xlsx")}; var pr by remember{mutableStateOf(s["report_period"]?: "all")}
    var ef by remember{mutableStateOf(false)}; var epr by remember{mutableStateOf(false)}
    var ed by remember{mutableStateOf(false)}; var ep by remember{mutableStateOf(false)}
    var etxt by remember{mutableStateOf(types.joinToString("\n"))}; var edir by remember{mutableStateOf(dirs.joinToString("\n"))}; var epts by remember{mutableStateOf(pts.joinToString("\n"))}

    Scaffold(topBar={TopAppBar(title={Text("Настройки")},navigationIcon={TextButton(onClick=onBack){Text("Назад")}})}){pd->
        Column(Modifier.fillMaxSize().padding(pd).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp), horizontalAlignment=Alignment.CenterHorizontally){
            // Системная тема
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Text("Системная тема")
                Spacer(Modifier.weight(1f))
                Switch(checked=systemTheme, onCheckedChange={systemTheme=it})
            }
            Divider()
            // Формат и период
            listOf(Triple("Формат",{ef},{fm}) to listOf("Excel" to "xlsx","CSV" to "csv"), Triple("Период",{epr},{pr}) to listOf("Сегодня" to "today","Неделя" to "week","Месяц" to "month","Всё" to "all")).forEach{(tr,op)->
                val (lb,exp,val_) = tr
                ExposedDropdownMenuBox(expanded=exp(),onExpandedChange={when(lb){"Формат"->ef=it;"Период"->epr=it}}){
                    OutlinedTextField(op.first{it.second==val_()}.first,{},readOnly=true,label={Text(lb)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(exp())},modifier=Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(exp(),{when(lb){"Формат"->ef=false;"Период"->epr=false}}){op.forEach{(n,v)->DropdownMenuItem(text={Text(n)},onClick={when(lb){"Формат"->fm=v;"Период"->pr=v};when(lb){"Формат"->ef=false;"Период"->epr=false}})}}
                }
            }
            Divider()
            // Направление и точка
            ExposedDropdownMenuBox(expanded=ed,onExpandedChange={ed=it}){OutlinedTextField(sd,{},readOnly=true,label={Text("Направление")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(ed)},modifier=Modifier.fillMaxWidth().menuAnchor()); ExposedDropdownMenu(ed,{ed=false}){dirs.forEach{d->DropdownMenuItem(text={Text(d)},onClick={sd=d;ed=false})}}}
            ExposedDropdownMenuBox(expanded=ep,onExpandedChange={ep=it}){OutlinedTextField(sp,{},readOnly=true,label={Text("Точка")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(ep)},modifier=Modifier.fillMaxWidth().menuAnchor()); ExposedDropdownMenu(ep,{ep=false}){pts.forEach{p->DropdownMenuItem(text={Text(p)},onClick={sp=p;ep=false})}}}
            Divider()
            OutlinedTextField(etxt,{etxt=it},label={Text("Типы")},modifier=Modifier.fillMaxWidth().height(150.dp),maxLines=20)
            OutlinedTextField(edir,{edir=it},label={Text("Направления")},modifier=Modifier.fillMaxWidth().height(120.dp),maxLines=15)
            OutlinedTextField(epts,{epts=it},label={Text("Точки")},modifier=Modifier.fillMaxWidth().height(120.dp),maxLines=15)
            Button(onClick={
                onSave(mapOf("direction" to sd,"point" to sp,"system_theme" to if(systemTheme) "true" else "false","export_format" to fm,"report_period" to pr),
                    mapOf("types" to etxt.split("\n").map{it.trim()}.filter{it.isNotEmpty()},"directions" to edir.split("\n").map{it.trim()}.filter{it.isNotEmpty()},"points" to epts.split("\n").map{it.trim()}.filter{it.isNotEmpty()}))
            },modifier=Modifier.fillMaxWidth()){Text("Сохранить")}
        }
    }
}
