package com.mercan.fuzulplanim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercan.fuzulplanim.MainViewModel
import com.mercan.fuzulplanim.data.*
import com.mercan.fuzulplanim.importer.StatementPreview
import com.mercan.fuzulplanim.util.tl0

@Composable
fun FileCenterScreen(vm:MainViewModel,imports:List<ImportBatchEntity>,onBudget:()->Unit,onStatement:()->Unit,onExport:()->Unit,onRestore:()->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{SectionTitle("Dosya Merkezi","Dosya seç → analiz → önizle → onayla → kaydet")};item{AppCard{Text("BÜTÇE / EXCEL",color=Blue,fontSize=11.sp,fontWeight=FontWeight.Bold);Text("Mevcut çalışma kitabını içe aktar",fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=4.dp));Text("SABIT_GİDER, Taksit Takibi, Aylık Plan, FUZUL_SOZLESME ve Tüm İşlemler okunur.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=5.dp));Button(onClick=onBudget,modifier=Modifier.padding(top=12.dp)){Icon(Icons.Rounded.TableView,null);Text(" Bütçe Excel'i seç")}}};item{AppCard{Text("EKSTRE / HESAP HAREKETİ",color=Teal,fontSize=11.sp,fontWeight=FontWeight.Bold);Text("PDF, Excel veya CSV yükle",fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=4.dp));Text("İşlemler önce önizlenir; onaylamadan veritabanına yazılmaz.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=5.dp));FilledTonalButton(onClick=onStatement,modifier=Modifier.padding(top=12.dp)){Icon(Icons.Rounded.UploadFile,null);Text(" Ekstre / hareket seç")}}};item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onExport,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.SaveAlt,null);Text(" Yedekle")};OutlinedButton(onClick=onRestore,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.Restore,null);Text(" Geri yükle")}}};if(imports.isNotEmpty())item{Text("İçe aktarma geçmişi",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))};items(imports,key={it.id}){x->AppCard{Row{Column(Modifier.weight(1f)){Text(x.fileName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${x.detectedType} • ${x.rowsImported}/${x.rowsFound}",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=3.dp))};Pill(x.status,if(x.status=="Başarılı")Green else Amber)}}}}}

@Composable
fun StatementPreviewDialog(preview:StatementPreview,onCancel:()->Unit,onImport:(String)->Unit){var source by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onCancel,title={Text("${preview.rows.size} işlem bulundu")},text={LazyColumn(Modifier.heightIn(max=520.dp)){item{FormField("Kaynak / banka (isteğe bağlı)",source,{source=it});Text("Önizleme",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp,bottom=6.dp))};items(preview.rows.take(12)){r->Row(Modifier.fillMaxWidth().padding(vertical=5.dp)){Column(Modifier.weight(1f)){Text(r.description,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${r.date} • ${r.category}",fontSize=10.sp,color=Muted)};Text(tl0(r.amount),fontSize=12.sp,color=if(r.amount>=0)Green else Red,fontWeight=FontWeight.Bold)}};if(preview.rows.size>12)item{Text("+ ${preview.rows.size-12} işlem daha",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))}}},confirmButton={TextButton(onClick={onImport(source)}){Text("Onayla ve kaydet")}},dismissButton={TextButton(onClick=onCancel){Text("Vazgeç")}})}

@Composable
fun ReportsScreen(vm:MainViewModel,transactions:List<TransactionEntity>,months:List<MonthlyPlanEntity>,fixed:List<FixedExpenseEntity>,installments:List<InstallmentEntity>){val categories=transactions.filter{it.includedInBudget&&it.amount<0}.groupBy{it.category}.mapValues{(_,v)->-v.sumOf{it.amount}}.entries.sortedByDescending{it.value}.take(8);LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{SectionTitle("Raporlar","Plan ve gerçekleşen finans görünümü")};item{AppCard{Text("PLANLANAN AY SONU",color=Blue,fontSize=11.sp,fontWeight=FontWeight.Bold);MiniLineChart(months.take(12).map{vm.repo.planNet(it,fixed,installments)},Modifier.padding(top=8.dp));if(months.isNotEmpty())Text("İlk 12 ay • aylık net kalan",fontSize=11.sp,color=Muted)}};item{AppCard{Text("GERÇEK HARCAMA KATEGORİLERİ",color=Teal,fontSize=11.sp,fontWeight=FontWeight.Bold);if(categories.isEmpty())Text("Ekstre yüklendikçe burada kategori dağılımı görünür.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))else ExpenseBars(categories.map{it.key to it.value},Modifier.padding(top=10.dp))}}}}
